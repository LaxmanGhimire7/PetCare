package com.example.petcare.ui.providers

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.design.applySystemBarTopPadding
import com.example.petcare.databinding.FragmentPlacePickerBinding
import com.example.petcare.location.PlaceLocation
import com.example.petcare.location.OpenStreetMapTiles
import com.example.petcare.location.PlaceMarkerIcon
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlinx.coroutines.launch

/** Lets the user drop a pin and reverse-geocodes it before returning to the place form. */
class PlacePickerFragment : Fragment() {
    private var _binding: FragmentPlacePickerBinding? = null
    private val binding get() = _binding!!
    private var mapView: MapView? = null
    private var pinSource: GeoJsonSource? = null
    private var pin: LatLng? = null
    private var address: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentPlacePickerBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        view.applySystemBarTopPadding()
        if (state != null && state.containsKey(LATITUDE) && state.containsKey(LONGITUDE)) {
            pin = LatLng(state.getDouble(LATITUDE), state.getDouble(LONGITUDE))
            address = state.getString(ADDRESS)
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
                            style.addImage(PIN_ICON, PlaceMarkerIcon.create(requireContext(), "shelter"))
                            val source = GeoJsonSource(PIN_SOURCE)
                            style.addSource(source)
                            style.addLayer(SymbolLayer(PIN_LAYER, PIN_SOURCE)
                                .withProperties(PropertyFactory.iconImage(PIN_ICON),
                                    PropertyFactory.iconAnchor("bottom"),
                                    PropertyFactory.iconAllowOverlap(true)))
                            pinSource = source
                            pin?.let { selected ->
                                source.setGeoJson(Feature.fromGeometry(
                                    Point.fromLngLat(selected.longitude, selected.latitude)))
                            }
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                pin ?: LatLng(0.0, 0.0), if (pin == null) 2.0 else 14.0))
                            map.addOnMapLongClickListener { point ->
                                if (_binding != null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    pin = point
                                    address = null
                                    source.setGeoJson(Feature.fromGeometry(
                                        Point.fromLngLat(point.longitude, point.latitude)))
                                    binding.usePinButton.isEnabled = true
                                    binding.pickerStatus.setText(R.string.pin_saved)
                                    // A later geocoder callback must not replace a newer selected pin.
                                    owner.lifecycleScope.launch {
                                        val found = PlaceLocation.reverseGeocode(requireContext(), point.latitude, point.longitude)
                                        if (_binding != null && pin == point) {
                                            address = found
                                            if (found == null) binding.pickerStatus.setText(R.string.geocode_unavailable)
                                            else binding.pickerStatus.text = found
                                        }
                                    }
                                }
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
            })
            findNavController().navigateUp()
        }
    }

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        pin?.let { outState.putDouble(LATITUDE, it.latitude); outState.putDouble(LONGITUDE, it.longitude) }
        outState.putString(ADDRESS, address)
        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroyView() {
        mapView?.onDestroy()
        mapView = null
        pinSource = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val PIN_RESULT = "selectedPlacePin"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val ADDRESS = "address"
        private const val TAG = "PetCarePlacePicker"
        private const val PIN_SOURCE = "selected-pin"
        private const val PIN_LAYER = "selected-pin-layer"
        private const val PIN_ICON = "selected-pin-icon"
    }
}
