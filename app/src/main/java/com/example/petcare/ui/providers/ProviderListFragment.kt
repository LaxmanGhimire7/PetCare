package com.example.petcare.ui.providers

import com.example.petcare.ui.UiSnackbar
import android.content.Intent
import android.content.ActivityNotFoundException
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import android.net.Uri
import android.os.Bundle
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.databinding.FragmentProviderListBinding
import com.example.petcare.databinding.ItemPlaceRowBinding
import com.example.petcare.ui.ScreenState
import com.example.petcare.location.PlaceLocation
import com.example.petcare.location.PlaceMarkerIcon
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Saved care places with direct map, dial and booking actions. */
class ProviderListFragment : Fragment() {
    private var _binding: FragmentProviderListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProviderListViewModel by viewModels()
    private var places: List<ProviderEntity> = emptyList()
    private var selectedType: String? = null
    private var userLocation: Location? = null
    private var map: GoogleMap? = null
    private var clusterManager: ClusterManager<PlaceMarker>? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) loadLocation() else permissionFallback()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = FragmentProviderListBinding.inflate(inflater, container, false).also { _binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        binding.addButton.setOnClickListener { findNavController().navigate(R.id.action_providers_to_add_provider) }
        binding.placeSearchButton.setOnClickListener { searchAddress() }
        binding.categoryFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedType = when (checkedIds.firstOrNull()) {
                R.id.filter_vet -> resources.getStringArray(R.array.provider_types)[0]
                R.id.filter_grooming -> resources.getStringArray(R.array.provider_types)[1]
                R.id.filter_parks -> resources.getStringArray(R.array.provider_types)[2]
                else -> null
            }
            render(places)
            updateMarkers()
        }
        binding.findVetsButton.setOnClickListener {
            val lat = userLocation?.latitude ?: 0.0
            val lon = userLocation?.longitude ?: 0.0
            openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=veterinary+clinic")))
        }
        parentFragmentManager.setFragmentResultListener(PLACE_ACTION, viewLifecycleOwner) { _, result ->
            val id = result.getLong(PLACE_ID)
            when (result.getString(PLACE_ACTION)) {
                EDIT -> findNavController().navigate(R.id.action_providers_to_add_provider,
                    Bundle().apply { putLong(PLACE_ID, id) })
                DELETE -> deletePlace(id)
            }
        }
        setupMap()
        requestLocation()
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.state.collect { state ->
                when (state) {
                    ScreenState.Loading -> binding.emptyText.visibility = View.GONE
                    ScreenState.Empty -> { places = emptyList(); render(places); updateMarkers() }
                    is ScreenState.Content -> { places = state.data; render(places); updateMarkers() }
                    is ScreenState.Error -> {
                        places = emptyList(); render(places); updateMarkers()
                        UiSnackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        } }
    }
    private fun render(items: List<ProviderEntity>) {
        val visibleItems = selectedType?.let { type -> items.filter { it.type == type } } ?: items
        binding.emptyText.visibility = if (visibleItems.isEmpty()) View.VISIBLE else View.GONE; binding.itemContainer.removeAllViews()
        val origin = userLocation
        val sorted = if (origin == null) visibleItems.sortedBy { it.name.lowercase() }
            else visibleItems.sortedWith(compareBy<ProviderEntity> {
                if (it.latitude == null || it.longitude == null) Double.MAX_VALUE
                else PlaceLocation.distanceKm(origin.latitude, origin.longitude, it.latitude, it.longitude)
            }.thenBy { it.name.lowercase() })
        sorted.forEach { provider ->
            val row = ItemPlaceRowBinding.inflate(layoutInflater, binding.itemContainer, false)
            row.placeName.text = provider.name
            val distance = if (origin != null && provider.latitude != null && provider.longitude != null)
                getString(R.string.place_distance_km, PlaceLocation.distanceKm(origin.latitude, origin.longitude,
                    provider.latitude, provider.longitude)) else getString(R.string.place_distance_unknown)
            row.placeDetail.text = getString(R.string.place_row_detail, provider.type, distance)
            row.root.setOnClickListener { PlaceDetailSheet.show(parentFragmentManager, provider, origin) }
            row.root.setOnLongClickListener {
                deletePlace(provider.id)
                true
            }
            binding.itemContainer.addView(row.root)
        }
    }
    @SuppressLint("PotentialBehaviorOverride")
    private fun setupMap() {
        if (!PlaceLocation.mapsAvailable(requireContext())) {
            binding.placeMapFrame.visibility = View.GONE
            binding.placeMapUnavailable.visibility = View.VISIBLE
            return
        }
        val fragment = SupportMapFragment.newInstance()
        childFragmentManager.beginTransaction().replace(R.id.place_map_frame, fragment).commitNow()
        fragment.getMapAsync { ready ->
            if (_binding == null) return@getMapAsync
            map = ready
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            ready.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(),
                if (night) R.raw.map_style_dark else R.raw.map_style_light))
            ready.uiSettings.isMapToolbarEnabled = false
            val manager = ClusterManager<PlaceMarker>(requireContext(), ready)
            manager.renderer = object : DefaultClusterRenderer<PlaceMarker>(requireContext(), ready, manager) {
                override fun onBeforeClusterItemRendered(item: PlaceMarker, options: MarkerOptions) {
                    options.icon(PlaceMarkerIcon.create(requireContext(), item.place.type))
                }
            }
            manager.setOnClusterItemClickListener { item ->
                PlaceDetailSheet.show(parentFragmentManager, item.place, userLocation)
                true
            }
            // ClusterManager owns both callbacks so marker taps and reclustering stay in sync.
            ready.setOnCameraIdleListener(manager)
            ready.setOnMarkerClickListener(manager)
            clusterManager = manager
            updateMarkers()
        }
    }

    private fun updateMarkers() {
        val manager = clusterManager ?: return
        manager.clearItems()
        val visiblePlaces = selectedType?.let { type -> places.filter { it.type == type } } ?: places
        manager.addItems(visiblePlaces.mapNotNull { place ->
            if (place.latitude == null || place.longitude == null) null else PlaceMarker(place)
        })
        manager.cluster()
        val first = visiblePlaces.firstOrNull { it.latitude != null && it.longitude != null }
        if (first != null && userLocation == null) map?.moveCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(first.latitude!!, first.longitude!!), 11f))
    }

    private fun requestLocation() {
        if (!PlaceLocation.playServicesAvailable(requireContext())) {
            binding.placeLocationStatus.setText(R.string.map_unavailable)
            return
        }
        if (hasPermission()) { loadLocation(); return }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.location_permission_title)
            .setMessage(R.string.location_permission_message)
            .setPositiveButton(R.string.allow) { _, _ ->
                requireContext().getSharedPreferences(PERMISSION_PREFS, Activity.MODE_PRIVATE)
                    .edit().putBoolean(REQUESTED, true).apply()
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                binding.placeLocationStatus.setText(R.string.location_permission_denied)
            }.show()
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun permissionFallback() {
        val blocked = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) &&
            requireContext().getSharedPreferences(PERMISSION_PREFS, Activity.MODE_PRIVATE)
                .getBoolean(REQUESTED, false)
        binding.placeLocationStatus.setText(if (blocked) R.string.location_permission_settings
            else R.string.location_permission_denied)
        if (blocked) MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.location_permission_settings)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                openIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${requireContext().packageName}")))
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun loadLocation() {
        if (!hasPermission() || !PlaceLocation.playServicesAvailable(requireContext())) return
        val service = requireContext().getSystemService(LocationManager::class.java)
        if (service == null || (!service.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    !service.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            binding.placeLocationStatus.setText(R.string.location_services_off)
            return
        }
        try {
            val client = LocationServices.getFusedLocationProviderClient(requireActivity())
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (_binding == null) return@addOnSuccessListener
                    if (location != null) applyLocation(location)
                    else client.lastLocation.addOnSuccessListener { last ->
                        if (_binding != null && last != null) applyLocation(last)
                    }
                }.addOnFailureListener {
                    if (_binding != null) binding.placeLocationStatus.setText(R.string.place_distance_unknown)
                }
        } catch (_: SecurityException) { permissionFallback() }
    }

    private fun applyLocation(location: Location) {
        userLocation = location
        binding.placeLocationStatus.setText(R.string.location_ready)
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 11f))
        render(places)
    }

    private fun deletePlace(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val deleted = viewModel.delete(id) ?: return@launch
            UiSnackbar.make(binding.root, R.string.place_deleted, Snackbar.LENGTH_LONG)
                .setDuration(6000).setAction(R.string.undo) {
                    viewLifecycleOwner.lifecycleScope.launch { viewModel.restore(deleted) }
                }.show()
        }
    }

    private fun searchAddress() {
        val query = binding.placeSearchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = PlaceLocation.search(requireContext(), query)
            if (result == null) UiSnackbar.make(binding.root, R.string.place_search_empty, Snackbar.LENGTH_LONG).show()
            else if (map != null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(result.first, result.second), 14f))
            else openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(
                "geo:${result.first},${result.second}?q=${Uri.encode(query)}")))
        }
    }

    /** Maps one saved place to a clusterable Google Maps marker. */
    private data class PlaceMarker(val place: ProviderEntity) : ClusterItem {
        override fun getPosition() = LatLng(place.latitude!!, place.longitude!!)
        override fun getTitle() = place.name
        override fun getSnippet() = place.address
        override fun getZIndex(): Float? = null
    }

    private fun openMap(provider: ProviderEntity) {
        val query = Uri.encode(provider.address.ifBlank { provider.name })
        val uri = if (provider.latitude != null && provider.longitude != null) Uri.parse("geo:${provider.latitude},${provider.longitude}?q=$query") else Uri.parse("geo:0,0?q=$query")
        openIntent(Intent(Intent.ACTION_VIEW, uri))
    }
    private fun openIntent(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { Toast.makeText(requireContext(), R.string.no_compatible_app, Toast.LENGTH_SHORT).show() }
    }
    override fun onDestroyView() { map = null; clusterManager = null; _binding = null; super.onDestroyView() }

    companion object {
        const val PLACE_ACTION = "placeAction"
        const val PLACE_ID = "placeId"
        const val EDIT = "edit"
        const val DELETE = "delete"
        const val PERMISSION_PREFS = "petcare_location"
        const val REQUESTED = "requested"
    }
}
