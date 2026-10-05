package com.example.petcare.ui.providers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.Manifest
import android.app.Activity
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.design.applySystemBarTopPadding
import com.example.petcare.design.MapGuard
import com.example.petcare.location.OpenStreetMapTiles
import com.example.petcare.location.NominatimPlaceSearch
import com.example.petcare.location.PlaceSearchResult
import com.example.petcare.location.NearbyCategory
import com.example.petcare.location.NearbyLoadResult
import com.example.petcare.location.NearbyOsmPlace
import com.example.petcare.location.NearbyOsmPlaces
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.databinding.FragmentProviderListBinding
import com.example.petcare.databinding.ItemPlaceRowBinding
import com.example.petcare.ui.ScreenState
import com.example.petcare.ui.UiSnackbar
import com.example.petcare.location.PlaceLocation
import com.example.petcare.location.PlaceMarkerIcon
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/** Saved care places with direct map, dial and booking actions. */
class ProviderListFragment : Fragment() {
    private var _binding: FragmentProviderListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProviderListViewModel by viewModels()
    private var places: List<ProviderEntity> = emptyList()
    private var nearbyPlaces: List<NearbyOsmPlace> = emptyList()
    private var nearbyLoading = false
    private var nearbyAttempted = false
    private var nearbyFailed = false
    private var nearbyJob: Job? = null
    private var pendingNearbyLookup = false
    private var searchJob: Job? = null
    private var selectedType: String? = null
    private var userLocation: Location? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var placeSource: GeoJsonSource? = null
    private var searchOverlay: SearchDestinationOverlay? = null
    private var searchCameraListener: MapLibreMap.OnCameraMoveListener? = null
    private var searchTarget: LatLng? = null
    private var searchTargetLabel: String? = null
    private var manualCameraFocus = false
    private var requestedPlaceId = 0L
    private val savingSuggestions = mutableSetOf<String>()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) loadLocation() else permissionFallback()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = FragmentProviderListBinding.inflate(inflater, container, false).also { _binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        requestedPlaceId = arguments?.getLong(PLACE_ID) ?: 0L
        if (state?.containsKey(SEARCH_LATITUDE) == true && state.containsKey(SEARCH_LONGITUDE)) {
            searchTarget = LatLng(state.getDouble(SEARCH_LATITUDE), state.getDouble(SEARCH_LONGITUDE))
            searchTargetLabel = state.getString(SEARCH_LABEL)
        }
        manualCameraFocus = state?.getBoolean(MANUAL_CAMERA_FOCUS) ?: false
        view.applySystemBarTopPadding()
        binding.saveSearchResultButton.visibility = if (searchTarget == null) View.GONE else View.VISIBLE
        binding.saveSearchResultButton.setOnClickListener {
            val target = searchTarget ?: return@setOnClickListener
            val label = searchTargetLabel.orEmpty()
            val name = label.substringBefore(',').trim().ifBlank { label }
            saveSuggestedLocation(name, label, target.latitude, target.longitude,
                resources.getStringArray(R.array.provider_types).last())
        }
        BottomSheetBehavior.from(binding.placeSheet).state = BottomSheetBehavior.STATE_COLLAPSED
        binding.addButton.setOnClickListener { findNavController().navigate(R.id.action_providers_to_add_provider) }
        binding.placeSearchButton.setOnClickListener { searchAddress() }
        binding.placeSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchAddress()
                true
            } else false
        }
        binding.placeSearchButton.setOnLongClickListener {
            val options = arrayOf(getString(R.string.place_search_provider_osm),
                getString(R.string.place_search_provider_device))
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.place_search_provider_title)
                .setMessage(R.string.place_search_provider_note)
                .setSingleChoiceItems(options,
                    if (NominatimPlaceSearch.usesPublicService(requireContext())) 0 else 1) { dialog, choice ->
                    NominatimPlaceSearch.setUsesPublicService(requireContext(), choice == 0)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
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
        listOf(binding.filterAll, binding.filterVet, binding.filterGrooming,
            binding.filterParks).forEach { chip ->
            chip.setOnClickListener {
                searchJob?.cancel()
                nearbyJob?.cancel()
                nearbyJob = null
                loadNearbyPlaces()
            }
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
        setupMap(state)
        requestLocation()
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.state.collect { state ->
                when (state) {
                    ScreenState.Loading -> binding.emptyText.visibility = View.GONE
                    ScreenState.Empty -> { places = emptyList(); render(places); updateMarkers() }
                    is ScreenState.Content -> {
                        places = state.data
                        render(places)
                        updateMarkers()
                        openRequestedPlace()
                    }
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
        val visibleNearby = nearbyPlaces.filter { selectedType == null || categoryType(it.category) == selectedType }
        binding.emptyText.visibility = if (visibleItems.isEmpty() && visibleNearby.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.setText(when {
            pendingNearbyLookup -> R.string.nearby_waiting_for_location
            nearbyLoading -> R.string.nearby_loading
            nearbyFailed -> R.string.nearby_unavailable
            nearbyAttempted -> R.string.nearby_empty
            else -> R.string.nearby_initial_hint
        })
        binding.itemContainer.removeAllViews()
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
            row.root.tag = provider.id
            row.root.setOnClickListener { PlaceDetailSheet.show(parentFragmentManager, provider, origin) }
            row.root.setOnLongClickListener {
                deletePlace(provider.id)
                true
            }
            binding.itemContainer.addView(row.root)
        }
        visibleNearby.sortedWith(compareBy<NearbyOsmPlace> {
            val centre = origin?.let { it.latitude to it.longitude }
                ?: map?.cameraPosition?.target?.let { target -> target.latitude to target.longitude }
            if (centre == null) Double.MAX_VALUE else PlaceLocation.distanceKm(
                centre.first, centre.second, it.latitude, it.longitude)
        }.thenBy { it.name.lowercase() }).forEach { place ->
            val row = ItemPlaceRowBinding.inflate(layoutInflater, binding.itemContainer, false)
            row.placeName.text = place.name
            val distance = if (origin == null) getString(R.string.place_distance_unknown)
                else getString(R.string.place_distance_km, PlaceLocation.distanceKm(origin.latitude,
                    origin.longitude, place.latitude, place.longitude))
            row.placeDetail.text = getString(R.string.nearby_place_row_detail,
                categoryType(place.category), distance)
            row.root.tag = place.osmKey
            row.root.setOnClickListener { showNearbyPlace(place) }
            binding.itemContainer.addView(row.root)
        }
    }
    private fun setupMap(state: Bundle?) {
        val view = try {
            OpenStreetMapTiles.initialize(requireContext().applicationContext)
            MapView(requireContext()).also { mapView ->
                binding.placeMapFrame.addView(mapView, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        } catch (e: Exception) {
            Log.e(TAG, "MapLibre map creation failed", e)
            showMapUnavailable()
            return
        }
        mapView = view
        val owner = viewLifecycleOwner
        try {
            view.onCreate(state)
        } catch (e: Exception) {
            Log.e(TAG, "MapLibre map initialization failed", e)
            runCatching { view.onDestroy() }
                .onFailure { Log.w(TAG, "MapLibre map cleanup failed", it) }
            binding.placeMapFrame.removeView(view)
            mapView = null
            showMapUnavailable()
            return
        }
        searchOverlay = SearchDestinationOverlay(requireContext()).also {
            binding.placeMapFrame.addView(it, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        view.addOnDidFailLoadingMapListener { error ->
            Log.w(TAG, "MapLibre map loading failed: $error")
            if (_binding != null && mapView === view) showMapUnavailable()
        }
        view.getMapAsync { ready ->
            owner.lifecycleScope.launch {
                owner.lifecycle.withStarted {
                    if (_binding == null || mapView !== view) return@withStarted
                    try {
                        ready.setStyle(OpenStreetMapTiles.style()) { style ->
                        if (_binding == null || mapView !== view ||
                            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@setStyle
                        try {
                            installPlaceLayers(style)
                            map = ready
                            val listener = MapLibreMap.OnCameraMoveListener { updateSearchOverlay() }
                            searchCameraListener = listener
                            ready.addOnCameraMoveListener(listener)
                            ready.addOnMapClickListener { point -> onMapTap(ready, point) }
                            updateMarkers()
                            userLocation?.let(::applyLocation)
                            searchTarget?.let { showSearchTarget(it, searchTargetLabel) }
                            if (pendingNearbyLookup) loadNearbyPlaces()
                        } catch (e: Exception) {
                            Log.e(TAG, "MapLibre place layers failed", e)
                            showMapUnavailable()
                        }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "MapLibre map style setup failed", e)
                        showMapUnavailable()
                    }
                }
            }
        }
    }

    private fun installPlaceLayers(style: Style) {
        listOf("clinic", "grooming", "park", "supply", "shelter").forEach { kind ->
            style.addImage("place-$kind", PlaceMarkerIcon.create(requireContext(), kind))
        }
        val source = GeoJsonSource(PLACE_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>()),
            GeoJsonOptions().withCluster(true).withClusterMaxZoom(14).withClusterRadius(50))
        style.addSource(source)
        placeSource = source
        style.addLayer(CircleLayer(CLUSTER_LAYER, PLACE_SOURCE)
            .withFilter(Expression.has("point_count"))
            .withProperties(PropertyFactory.circleColor(resources.getColor(R.color.primary, requireContext().theme)),
                PropertyFactory.circleRadius(20f)))
        style.addLayer(SymbolLayer(CLUSTER_COUNT_LAYER, PLACE_SOURCE)
            .withFilter(Expression.has("point_count"))
            .withProperties(PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
                PropertyFactory.textFont(arrayOf("Open Sans Regular")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor(resources.getColor(R.color.on_primary, requireContext().theme))))
        style.addLayer(SymbolLayer(PIN_LAYER, PLACE_SOURCE)
            .withFilter(Expression.not(Expression.has("point_count")))
            .withProperties(PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true)))
    }

    private fun onMapTap(ready: MapLibreMap, point: LatLng): Boolean {
        val pixel = ready.projection.toScreenLocation(point)
        ready.queryRenderedFeatures(pixel, PIN_LAYER).firstOrNull()?.let { feature ->
            if (feature.hasProperty("placeId")) feature.getNumberProperty("placeId")
                ?.toLong()?.let(::revealPlace)
            else feature.getStringProperty("nearbyKey")?.let { key ->
                nearbyPlaces.firstOrNull { it.osmKey == key }?.let(::showNearbyPlace)
            }
            return true
        }
        ready.queryRenderedFeatures(pixel, CLUSTER_LAYER).firstOrNull()?.let { cluster ->
            val source = placeSource ?: return false
            val zoom = source.getClusterExpansionZoom(cluster).toDouble()
            val position = cluster.geometry() as? Point ?: return false
            ready.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(position.latitude(), position.longitude()), zoom))
            return true
        }
        return false
    }

    private fun updateMarkers() {
        val source = placeSource ?: return
        val visiblePlaces = selectedType?.let { type -> places.filter { it.type == type } } ?: places
        val features = visiblePlaces.mapNotNull { place ->
            val latitude = place.latitude ?: return@mapNotNull null
            val longitude = place.longitude ?: return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
                addNumberProperty("placeId", place.id)
                addStringProperty("title", place.name)
                addStringProperty("description", place.address)
                addStringProperty("icon", "place-${PlaceMarkerIcon.keyFor(place.type)}")
            }
        }.toMutableList()
        nearbyPlaces.filter { selectedType == null || categoryType(it.category) == selectedType }
            .forEach { place ->
                features += Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude)).apply {
                    addStringProperty("nearbyKey", place.osmKey)
                    addStringProperty("title", place.name)
                    addStringProperty("icon", "place-${PlaceMarkerIcon.keyFor(categoryType(place.category))}")
                }
            }
        source.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
        val first = visiblePlaces.firstOrNull { it.latitude != null && it.longitude != null }
        if (first != null && userLocation == null && !manualCameraFocus && !nearbyAttempted) map?.moveCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(first.latitude!!, first.longitude!!), 11.0))
    }

    private fun showMapUnavailable() {
        val current = _binding ?: return
        current.placeMapFrame.visibility = View.GONE
        current.placeMapAttribution.visibility = View.GONE
        current.placeMapUnavailable.root.visibility = View.VISIBLE
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
                stopWaitingForNearbyLocation()
            }.show()
    }

    private fun hasPermission(): Boolean = MapGuard.hasLocationPermission(requireContext())

    private fun permissionFallback() {
        stopWaitingForNearbyLocation()
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
            stopWaitingForNearbyLocation()
            return
        }
        try {
            val owner = viewLifecycleOwner
            val client = LocationServices.getFusedLocationProviderClient(requireActivity())
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (_binding == null || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@addOnSuccessListener
                    if (location != null) applyLocation(location)
                    else client.lastLocation.addOnSuccessListener { last ->
                        if (_binding != null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            if (last != null) applyLocation(last) else stopWaitingForNearbyLocation()
                        }
                    }.addOnFailureListener {
                        Log.w(TAG, "Last location unavailable", it)
                        stopWaitingForNearbyLocation()
                    }
                }.addOnFailureListener {
                    Log.w(TAG, "Current location unavailable", it)
                    if (_binding != null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        binding.placeLocationStatus.setText(R.string.place_distance_unknown)
                    stopWaitingForNearbyLocation()
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission changed while requesting location", e)
            permissionFallback()
        }
    }

    private fun applyLocation(location: Location) {
        userLocation = location
        binding.placeLocationStatus.setText(R.string.location_ready)
        if (!manualCameraFocus) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(location.latitude, location.longitude), 15.0))
        render(places)
        if (pendingNearbyLookup) loadNearbyPlaces()
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

    /** Expands the sheet and scrolls the distance-sorted list to a tapped marker. */
    private fun revealPlace(placeId: Long) {
        BottomSheetBehavior.from(binding.placeSheet).state = BottomSheetBehavior.STATE_EXPANDED
        binding.placeSheet.post {
            val current = _binding ?: return@post
            val row = (0 until current.itemContainer.childCount)
                .map(current.itemContainer::getChildAt)
                .firstOrNull { it.tag == placeId }
            if (row != null) current.placeSheet.smoothScrollTo(0,
                current.itemContainer.top + row.top)
        }
    }

    private fun openRequestedPlace() {
        val place = places.firstOrNull { it.id == requestedPlaceId } ?: return
        requestedPlaceId = 0L
        revealPlace(place.id)
        PlaceDetailSheet.show(parentFragmentManager, place, userLocation)
    }

    private fun searchAddress() {
        val query = binding.placeSearchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank() || searchJob?.isActive == true) return
        clearSearchTarget()
        nearbyJob?.cancel()
        nearbyJob = null
        pendingNearbyLookup = false
        nearbyLoading = false
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.placeSearchButton.isEnabled = false
            binding.placeSearchButton.setText(R.string.place_searching)
            setSearchStatus(getString(R.string.place_searching))
            try {
                val result = withTimeoutOrNull(12_000L) {
                    if (NominatimPlaceSearch.usesPublicService(requireContext())) {
                        val visible = map?.cameraPosition?.takeIf { it.zoom >= 5.0 }?.target
                        val near = visible?.let { it.latitude to it.longitude }
                            ?: userLocation?.let { it.latitude to it.longitude }
                        NominatimPlaceSearch.search(requireContext(), query, near)
                    } else {
                        PlaceLocation.search(requireContext(), query)?.let {
                            PlaceSearchResult.Found(it.first, it.second, query)
                        } ?: PlaceSearchResult.NotFound
                    }
                } ?: PlaceSearchResult.Unavailable
                if (_binding == null) return@launch
                when (result) {
                    is PlaceSearchResult.Found -> {
                        val target = LatLng(result.latitude, result.longitude)
                        searchTarget = target
                        searchTargetLabel = result.label
                        binding.saveSearchResultButton.visibility = View.VISIBLE
                        showSearchTarget(target, result.label)
                        setSearchStatus(getString(R.string.place_search_showing, result.label))
                        UiSnackbar.make(binding.root,
                            getString(R.string.place_search_showing, result.label),
                            Snackbar.LENGTH_SHORT).show()
                    }
                    PlaceSearchResult.NotFound -> {
                        val message = if (NominatimPlaceSearch.usesPublicService(requireContext()))
                            R.string.place_search_empty else R.string.place_search_empty_device
                        setSearchStatus(getString(message))
                        UiSnackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                    PlaceSearchResult.Unavailable -> {
                        setSearchStatus(getString(R.string.place_search_unavailable))
                        UiSnackbar.make(binding.root,
                            R.string.place_search_unavailable, Snackbar.LENGTH_LONG).show()
                    }
                }
            } finally {
                _binding?.placeSearchButton?.apply {
                    isEnabled = true
                    setText(R.string.search_place)
                }
            }
        }
    }

    private fun showSearchTarget(target: LatLng, label: String?) {
        manualCameraFocus = true
        searchTarget = target
        searchTargetLabel = label
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.placeSearchInput.windowToken, 0)
        binding.placeSearchInput.clearFocus()
        BottomSheetBehavior.from(binding.placeSheet).state = BottomSheetBehavior.STATE_COLLAPSED
        // Centre the destination in the part of the map not covered by the toolbar/sheet.
        val top = 160.0 * resources.displayMetrics.density
        val bottom = BottomSheetBehavior.from(binding.placeSheet).peekHeight.toDouble()
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
            .target(target).zoom(16.5).padding(0.0, top, 0.0, bottom).build()))
        searchOverlay?.post { updateSearchOverlay() }
    }

    private fun updateSearchOverlay() {
        val destination = searchTarget ?: return
        val ready = map ?: return
        val point = ready.projection.toScreenLocation(destination)
        searchOverlay?.showAt(point.x, point.y, searchTargetLabel)
    }

    private fun setSearchStatus(message: String?) {
        val current = _binding ?: return
        current.placeSearchStatus.visibility = if (message == null) View.GONE else View.VISIBLE
        current.placeSearchStatus.text = message.orEmpty()
    }

    private fun clearSearchTarget() {
        searchTarget = null
        searchTargetLabel = null
        _binding?.saveSearchResultButton?.visibility = View.GONE
        searchOverlay?.clear()
        setSearchStatus(null)
    }

    private fun categoryType(category: NearbyCategory): String {
        val types = resources.getStringArray(R.array.provider_types)
        return when (category) {
            NearbyCategory.VET -> types[0]
            NearbyCategory.GROOMING -> types[1]
            NearbyCategory.PARK -> types[2]
        }
    }

    private fun loadNearbyPlaces() {
        val centre = map?.cameraPosition?.takeIf { it.zoom >= 9.0 }?.target
        val latitude = centre?.latitude ?: userLocation?.latitude
        val longitude = centre?.longitude ?: userLocation?.longitude
        if (latitude == null || longitude == null) {
            val service = requireContext().getSystemService(LocationManager::class.java)
            val locationEnabled = service != null &&
                (service.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    service.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            if (!hasPermission() || !locationEnabled) {
                pendingNearbyLookup = false
                nearbyLoading = false
                nearbyAttempted = false
                setSearchStatus(getString(R.string.nearby_location_unavailable))
                render(places)
                return
            }
            pendingNearbyLookup = true
            nearbyLoading = true
            nearbyAttempted = true
            setSearchStatus(getString(R.string.nearby_waiting_for_location))
            render(places)
            loadLocation()
            return
        }
        pendingNearbyLookup = false
        if (nearbyJob?.isActive == true) return
        nearbyPlaces = emptyList()
        nearbyLoading = true
        nearbyFailed = false
        nearbyAttempted = true
        setSearchStatus(getString(R.string.nearby_loading))
        render(places)
        val context = requireContext().applicationContext
        val types = resources.getStringArray(R.array.provider_types)
        val categories = when (selectedType) {
            types[0] -> listOf(NearbyCategory.VET)
            types[1] -> listOf(NearbyCategory.GROOMING)
            types[2] -> listOf(NearbyCategory.PARK)
            else -> NearbyCategory.entries
        }
        nearbyJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = withTimeoutOrNull(60_000L) {
                val found = mutableListOf<NearbyOsmPlace>()
                var incomplete = false
                categories.forEach { category ->
                    when (val categoryResult = NearbyOsmPlaces.load(context, latitude,
                        longitude, category)) {
                        is NearbyLoadResult.Success -> found += categoryResult.places
                        NearbyLoadResult.Unavailable -> incomplete = true
                    }
                }
                if (found.isNotEmpty() || !incomplete)
                    NearbyLoadResult.Success(found, incomplete)
                else NearbyLoadResult.Unavailable
            } ?: NearbyLoadResult.Unavailable
            if (_binding == null) return@launch
            nearbyLoading = false
            when (result) {
                is NearbyLoadResult.Success -> {
                    nearbyPlaces = result.places
                    nearbyFailed = result.incomplete && nearbyPlaces.isEmpty()
                    val count = nearbyPlaces.count {
                        selectedType == null || categoryType(it.category) == selectedType
                    }
                    setSearchStatus(getString(if (result.incomplete)
                        R.string.nearby_partial_result_count else R.string.nearby_result_count, count))
                }
                NearbyLoadResult.Unavailable -> {
                    nearbyFailed = true
                    setSearchStatus(getString(R.string.nearby_unavailable))
                    UiSnackbar.make(binding.root, R.string.nearby_unavailable, Snackbar.LENGTH_LONG).show()
                }
            }
            render(places)
            updateMarkers()
        }
    }

    private fun stopWaitingForNearbyLocation() {
        if (!pendingNearbyLookup || _binding == null) return
        pendingNearbyLookup = false
        nearbyLoading = false
        nearbyAttempted = false
        setSearchStatus(getString(R.string.nearby_location_unavailable))
        render(places)
    }

    private fun showNearbyPlace(place: NearbyOsmPlace) {
        val target = LatLng(place.latitude, place.longitude)
        manualCameraFocus = true
        BottomSheetBehavior.from(binding.placeSheet).state = BottomSheetBehavior.STATE_COLLAPSED
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(place.name)
            .setMessage(R.string.location_suggestion_message)
            .setPositiveButton(R.string.location_save_suggestion) { _, _ ->
                saveSuggestedLocation(place.name, null, place.latitude, place.longitude,
                    categoryType(place.category))
            }
            .setNeutralButton(R.string.directions) { _, _ ->
                openIntent(Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:${place.latitude},${place.longitude}?q=${Uri.encode(place.name)}")))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveSuggestedLocation(name: String, address: String?, latitude: Double,
                                      longitude: Double, type: String) {
        val key = "$latitude,$longitude"
        if (!savingSuggestions.add(key)) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (places.any { it.latitude != null && it.longitude != null &&
                        abs(it.latitude - latitude) < 0.00001 && abs(it.longitude - longitude) < 0.00001 }) {
                    UiSnackbar.make(binding.root, R.string.location_already_saved, Snackbar.LENGTH_SHORT).show()
                    return@launch
                }
                val savedAddress = address?.takeIf(String::isNotBlank)
                    ?: withTimeoutOrNull(5_000L) {
                        PlaceLocation.reverseGeocode(requireContext(), latitude, longitude)
                    }.orEmpty().ifBlank { name }
                PetCareRepositories(requireContext()).places.add(ProviderEntity(
                    name = name, type = type, address = savedAddress,
                    latitude = latitude, longitude = longitude,
                ))
                Toast.makeText(requireContext(), R.string.location_saved_for_tasks,
                    Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                UiSnackbar.make(binding.root, R.string.location_save_failed, Snackbar.LENGTH_LONG).show()
            } finally {
                savingSuggestions.remove(key)
            }
        }
    }

    private fun openMap(provider: ProviderEntity) {
        val query = Uri.encode(provider.address.ifBlank { provider.name })
        val uri = if (provider.latitude != null && provider.longitude != null) Uri.parse("geo:${provider.latitude},${provider.longitude}?q=$query") else Uri.parse("geo:0,0?q=$query")
        openIntent(Intent(Intent.ACTION_VIEW, uri))
    }
    private fun openIntent(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { Toast.makeText(requireContext(), R.string.no_compatible_app, Toast.LENGTH_SHORT).show() }
    }
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        searchTarget?.let {
            outState.putDouble(SEARCH_LATITUDE, it.latitude)
            outState.putDouble(SEARCH_LONGITUDE, it.longitude)
            outState.putString(SEARCH_LABEL, searchTargetLabel)
        }
        outState.putBoolean(MANUAL_CAMERA_FOCUS, manualCameraFocus)
        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroyView() {
        pendingNearbyLookup = false
        nearbyJob?.cancel()
        nearbyJob = null
        searchJob?.cancel()
        searchJob = null
        searchCameraListener?.let { listener -> map?.removeOnCameraMoveListener(listener) }
        searchCameraListener = null
        mapView?.onDestroy()
        mapView = null
        map = null
        placeSource = null
        searchOverlay = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val PLACE_ACTION = "placeAction"
        const val PLACE_ID = "placeId"
        const val EDIT = "edit"
        const val DELETE = "delete"
        const val PERMISSION_PREFS = "petcare_location"
        const val REQUESTED = "requested"
        private const val TAG = "PetCarePlaces"
        private const val PLACE_SOURCE = "saved-places"
        private const val CLUSTER_LAYER = "place-clusters"
        private const val CLUSTER_COUNT_LAYER = "place-cluster-count"
        private const val PIN_LAYER = "place-pins"
        private const val SEARCH_LATITUDE = "searchedPlaceLatitude"
        private const val SEARCH_LONGITUDE = "searchedPlaceLongitude"
        private const val SEARCH_LABEL = "searchedPlaceLabel"
        private const val MANUAL_CAMERA_FOCUS = "manualCameraFocus"
    }
}
