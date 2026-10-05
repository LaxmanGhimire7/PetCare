package com.example.petcare.ui.providers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.design.applySystemBarPaddingWithKeyboard
import com.example.petcare.databinding.FragmentPlacePickerBinding
import com.example.petcare.location.PlaceLocation
import com.example.petcare.location.NominatimPlaceSearch
import com.example.petcare.location.PlaceSearchResult
import com.example.petcare.location.OpenStreetMapTiles
import com.example.petcare.location.PlaceMarkerIcon
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Lets the user drop a pin and reverse-geocodes it before returning to the place form. */
class PlacePickerFragment : Fragment() {
    private var _binding: FragmentPlacePickerBinding? = null
    private val binding get() = _binding!!
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var mapReady = false
    private var pendingCenter: LatLng? = null
    private var pinSource: GeoJsonSource? = null
    private var pin: LatLng? = null
    private var address: String? = null
    private var selectedName: String? = null
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) centerOnCurrentLocation()
        else _binding?.pickerStatus?.setText(R.string.location_permission_denied)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentPlacePickerBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        view.applySystemBarPaddingWithKeyboard()
        binding.pickerSearchButton.setOnClickListener { searchPlace() }
        binding.pickerSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlace()
                true
            } else false
        }
        binding.pickerCurrentLocationButton.setOnClickListener {
            if (hasLocationPermission()) centerOnCurrentLocation()
            else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (state != null && state.containsKey(LATITUDE) && state.containsKey(LONGITUDE)) {
            pin = LatLng(state.getDouble(LATITUDE), state.getDouble(LONGITUDE))
            address = state.getString(ADDRESS)
            selectedName = state.getString(PLACE_NAME)
            binding.usePinButton.isEnabled = true
        }
        val mapView = try {
            OpenStreetMapTiles.initialize(requireContext().applicationContext)
            MapView(requireContext()).also { created ->
                binding.pickerMapFrame.addView(created, 0, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        } catch (e: Exception) {
            Log.e(TAG, "MapLibre picker creation failed", e)
            binding.pickerMapFrame.visibility = View.GONE
            binding.pickerStatus.setText(R.string.map_unavailable)
            return
        }
        this.mapView = mapView
        val owner = viewLifecycleOwner
        try {
            mapView.onCreate(state)
        } catch (e: Exception) {
            Log.e(TAG, "MapLibre picker initialization failed", e)
            runCatching { mapView.onDestroy() }
                .onFailure { Log.w(TAG, "MapLibre picker cleanup failed", it) }
            binding.pickerMapFrame.removeView(mapView)
            this.mapView = null
            binding.pickerMapFrame.visibility = View.GONE
            binding.pickerStatus.setText(R.string.map_unavailable)
            return
        }
        mapView.addOnDidFailLoadingMapListener { error ->
            Log.w(TAG, "MapLibre picker loading failed: $error")
            if (_binding != null && this.mapView === mapView) binding.pickerStatus.setText(R.string.map_unavailable)
        }
        mapView.getMapAsync { map ->
            owner.lifecycleScope.launch {
                owner.lifecycle.withStarted {
                    if (_binding == null || this@PlacePickerFragment.mapView !== mapView) return@withStarted
                    try {
                        map.setStyle(OpenStreetMapTiles.style()) { style ->
                        if (_binding == null || this@PlacePickerFragment.mapView !== mapView ||
                            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@setStyle
                        try {
                            style.addImage(PIN_ICON, PlaceMarkerIcon.createSearch(requireContext()))
                            val source = GeoJsonSource(PIN_SOURCE)
                            style.addSource(source)
                            style.addLayer(SymbolLayer(PIN_LAYER, PIN_SOURCE)
                                .withProperties(PropertyFactory.iconImage(PIN_ICON),
                                    PropertyFactory.iconAnchor("bottom"),
                                    PropertyFactory.iconAllowOverlap(true)))
                            pinSource = source
                            this@PlacePickerFragment.map = map
                            mapReady = true
                            pin?.let { selected ->
                                source.setGeoJson(Feature.fromGeometry(
                                    Point.fromLngLat(selected.longitude, selected.latitude)))
                            }
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                pin ?: pendingCenter ?: KATHMANDU,
                                if (pin == null && pendingCenter == null) 13.5 else 16.0))
                            if (pin == null && pendingCenter == null && hasLocationPermission()) {
                                centerOnCurrentLocation()
                            }
                            map.addOnMapClickListener { point ->
                                selectPin(point)
                                true
                            }
                            map.addOnMapLongClickListener { point ->
                                selectPin(point)
                                true
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "MapLibre picker style failed", e)
                            binding.pickerStatus.setText(R.string.map_unavailable)
                        }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "MapLibre picker style setup failed", e)
                        binding.pickerStatus.setText(R.string.map_unavailable)
                    }
                }
            }
        }
        binding.usePinButton.setOnClickListener {
            val selected = pin ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(PIN_RESULT, Bundle().apply {
                putDouble(LATITUDE, selected.latitude)
                putDouble(LONGITUDE, selected.longitude)
                putString(ADDRESS, address)
                putString(PLACE_NAME, selectedName)
            })
            findNavController().navigateUp()
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun centerOnCurrentLocation() {
        if (!hasLocationPermission() || !PlaceLocation.playServicesAvailable(requireContext())) {
            _binding?.pickerStatus?.setText(R.string.location_picker_current_unavailable)
            return
        }
        try {
            val client = LocationServices.getFusedLocationProviderClient(requireActivity())
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (_binding == null) return@addOnSuccessListener
                    if (location != null) {
                        focus(LatLng(location.latitude, location.longitude))
                        binding.pickerStatus.setText(R.string.location_picker_current_ready)
                    } else client.lastLocation.addOnSuccessListener { last ->
                        if (_binding == null) return@addOnSuccessListener
                        if (last != null) {
                            focus(LatLng(last.latitude, last.longitude))
                            binding.pickerStatus.setText(R.string.location_picker_current_ready)
                        } else binding.pickerStatus.setText(R.string.location_picker_current_unavailable)
                    }
                }.addOnFailureListener {
                    _binding?.pickerStatus?.setText(R.string.location_picker_current_unavailable)
                }
        } catch (_: SecurityException) {
            _binding?.pickerStatus?.setText(R.string.location_permission_denied)
        }
    }

    private fun focus(point: LatLng) {
        pendingCenter = point
        if (mapReady) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0))
    }

    private fun searchPlace() {
        val query = binding.pickerSearchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            binding.pickerStatus.setText(R.string.location_picker_search_required)
            return
        }
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.pickerSearchInput.windowToken, 0)
        binding.pickerSearchInput.clearFocus()
        viewLifecycleOwner.lifecycleScope.launch {
            binding.pickerSearchButton.isEnabled = false
            binding.pickerStatus.setText(R.string.place_searching)
            try {
                val result = withTimeoutOrNull(12_000L) {
                    val near = map?.cameraPosition?.takeIf { it.zoom >= 5.0 }?.target
                        ?.let { it.latitude to it.longitude }
                    val online = if (NominatimPlaceSearch.usesPublicService(requireContext()))
                        NominatimPlaceSearch.search(requireContext(), query, near)
                    else PlaceSearchResult.Unavailable
                    if (online == PlaceSearchResult.Unavailable) {
                        PlaceLocation.search(requireContext(), query)?.let {
                            PlaceSearchResult.Found(it.first, it.second, query)
                        } ?: PlaceSearchResult.Unavailable
                    } else online
                } ?: PlaceSearchResult.Unavailable
                if (_binding == null) return@launch
                when (result) {
                    is PlaceSearchResult.Found -> {
                        val point = LatLng(result.latitude, result.longitude)
                        selectPin(point, query, result.label)
                        focus(point)
                        binding.pickerStatus.text = getString(R.string.location_picker_search_found,
                            result.label)
                    }
                    PlaceSearchResult.NotFound -> binding.pickerStatus.setText(R.string.place_search_empty)
                    PlaceSearchResult.Unavailable -> binding.pickerStatus.setText(R.string.place_search_unavailable)
                }
            } finally {
                _binding?.pickerSearchButton?.isEnabled = true
            }
        }
    }

    private fun selectPin(point: LatLng, fallbackAddress: String? = null,
                          name: String? = null) {
        if (_binding == null) return
        pin = point
        address = fallbackAddress
        selectedName = name
        pinSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(point.longitude,
            point.latitude)))
        binding.usePinButton.isEnabled = true
        binding.pickerStatus.setText(R.string.pin_saved)
        viewLifecycleOwner.lifecycleScope.launch {
            val found = withTimeoutOrNull(5_000L) {
                PlaceLocation.reverseGeocode(requireContext(), point.latitude, point.longitude)
            }
            if (_binding != null && pin == point) {
                address = found ?: fallbackAddress
                binding.pickerStatus.text = address ?: getString(R.string.geocode_unavailable)
            }
        }
    }

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        pin?.let { outState.putDouble(LATITUDE, it.latitude); outState.putDouble(LONGITUDE, it.longitude) }
        outState.putString(ADDRESS, address)
        outState.putString(PLACE_NAME, selectedName)
        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroyView() {
        mapView?.onDestroy()
        mapView = null
        map = null
        mapReady = false
        pinSource = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val PIN_RESULT = "selectedPlacePin"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val ADDRESS = "address"
        const val PLACE_NAME = "placeName"
        private const val TAG = "PetCarePlacePicker"
        private const val PIN_SOURCE = "selected-pin"
        private const val PIN_LAYER = "selected-pin-layer"
        private const val PIN_ICON = "selected-pin-icon"
        private val KATHMANDU = LatLng(27.7172, 85.3240)
    }
}
