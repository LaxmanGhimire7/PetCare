package com.example.petcare.ui.providers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.example.petcare.R
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.databinding.SheetPlaceDetailBinding
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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/** Saved-place detail with a lite map and actions that return edit/delete to the list. */
class PlaceDetailSheet : BottomSheetDialogFragment() {
    private var _binding: SheetPlaceDetailBinding? = null
    private val binding get() = _binding!!
    private var preview: MapView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        SheetPlaceDetailBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        val args = requireArguments()
        val name = args.getString(NAME).orEmpty()
        val address = args.getString(ADDRESS).orEmpty()
        val lat = args.getDouble(LAT, Double.NaN)
        val lon = args.getDouble(LON, Double.NaN)
        binding.placeName.text = name
        binding.placeAddress.text = address
        binding.placeHours.text = args.getString(HOURS).orEmpty().ifBlank {
            getString(R.string.place_hours_unknown)
        }
        val distance = args.getDouble(DISTANCE, Double.NaN)
        binding.placeDistance.text = if (distance.isNaN()) getString(R.string.place_distance_unknown)
            else getString(R.string.place_distance_km, distance)
        if (lat.isFinite() && lon.isFinite()) {
            setupPreview(state, lat, lon)
        } else {
            showPreviewUnavailable()
        }
        binding.directionsButton.setOnClickListener {
            val query = Uri.encode(address.ifBlank { name })
            open(Intent(Intent.ACTION_VIEW, Uri.parse(
                "geo:${if (lat.isFinite()) lat else 0.0},${if (lon.isFinite()) lon else 0.0}?q=$query")))
        }
        val phone = args.getString(PHONE).orEmpty()
        binding.callButton.visibility = if (phone.isBlank()) View.GONE else View.VISIBLE
        binding.callButton.setOnClickListener { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
        val booking = args.getString(BOOKING).orEmpty()
        binding.bookButton.visibility = if (booking.isBlank()) View.GONE else View.VISIBLE
        binding.bookButton.setOnClickListener {
            val url = if (booking.startsWith("http://") || booking.startsWith("https://")) booking
                else "https://$booking"
            open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        binding.editButton.setOnClickListener { action(ProviderListFragment.EDIT) }
        binding.deleteButton.setOnClickListener { action(ProviderListFragment.DELETE) }
    }

    private fun setupPreview(state: Bundle?, lat: Double, lon: Double) {
        val mapView = try {
            OpenStreetMapTiles.initialize(requireContext().applicationContext)
            MapView(requireContext())
        } catch (e: Exception) {
            Log.e(TAG, "MapLibre preview creation failed", e)
            showPreviewUnavailable()
            return
        }
        preview = mapView
        binding.placePreviewFrame.addView(mapView, 0)
        try {
            mapView.onCreate(state)
        } catch (e: Exception) {
            Log.e(TAG, "MapLibre preview initialization failed", e)
            runCatching { mapView.onDestroy() }
                .onFailure { Log.w(TAG, "MapLibre preview cleanup failed", it) }
            binding.placePreviewFrame.removeView(mapView)
            preview = null
            showPreviewUnavailable()
            return
        }
        mapView.addOnDidFailLoadingMapListener { error ->
            Log.w(TAG, "MapLibre preview loading failed: $error")
            if (_binding != null && preview === mapView) showPreviewUnavailable()
        }
        val owner = viewLifecycleOwner
        mapView.getMapAsync { map ->
            owner.lifecycleScope.launch {
                owner.lifecycle.withStarted {
                    if (_binding == null || preview !== mapView) return@withStarted
                    try {
                        map.setStyle(OpenStreetMapTiles.style()) { style ->
                        if (_binding == null || preview !== mapView ||
                            !owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@setStyle
                        try {
                            style.addImage(PREVIEW_ICON, PlaceMarkerIcon.create(requireContext(), "shelter"))
                            style.addSource(GeoJsonSource(PREVIEW_SOURCE,
                                Feature.fromGeometry(Point.fromLngLat(lon, lat))))
                            style.addLayer(SymbolLayer(PREVIEW_LAYER, PREVIEW_SOURCE)
                                .withProperties(PropertyFactory.iconImage(PREVIEW_ICON),
                                    PropertyFactory.iconAnchor("bottom")))
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 14.0))
                            map.uiSettings.isScrollGesturesEnabled = false
                            map.uiSettings.isZoomGesturesEnabled = false
                            map.uiSettings.isRotateGesturesEnabled = false
                            map.uiSettings.isTiltGesturesEnabled = false
                        } catch (e: Exception) {
                            Log.e(TAG, "MapLibre preview style failed", e)
                            showPreviewUnavailable()
                        }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "MapLibre preview style setup failed", e)
                        showPreviewUnavailable()
                    }
                }
            }
        }
    }

    private fun showPreviewUnavailable() {
        val current = _binding ?: return
        current.placePreviewFrame.visibility = View.GONE
        current.placePreviewFallback.visibility = View.VISIBLE
    }

    private fun action(kind: String) {
        parentFragmentManager.setFragmentResult(ProviderListFragment.PLACE_ACTION,
            Bundle().apply {
                putString(ProviderListFragment.PLACE_ACTION, kind)
                putLong(ProviderListFragment.PLACE_ID, requireArguments().getLong(ID))
            })
        dismiss()
    }

    private fun open(intent: Intent) {
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_compatible_app, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() { super.onStart(); preview?.onStart() }
    override fun onResume() { super.onResume(); preview?.onResume() }
    override fun onPause() { preview?.onPause(); super.onPause() }
    override fun onStop() { preview?.onStop(); super.onStop() }
    override fun onLowMemory() { preview?.onLowMemory(); super.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        preview?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() {
        preview?.onDestroy()
        preview = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ID = "id"
        private const val NAME = "name"
        private const val ADDRESS = "address"
        private const val HOURS = "hours"
        private const val PHONE = "phone"
        private const val BOOKING = "booking"
        private const val LAT = "lat"
        private const val LON = "lon"
        private const val DISTANCE = "distance"
        private const val TAG = "PetCarePlaceDetail"
        private const val PREVIEW_SOURCE = "preview-place"
        private const val PREVIEW_LAYER = "preview-place-pin"
        private const val PREVIEW_ICON = "preview-pin-icon"

        fun show(manager: FragmentManager, place: ProviderEntity, origin: Location?) {
            val distance = if (origin != null && place.latitude != null && place.longitude != null)
                PlaceLocation.distanceKm(origin.latitude, origin.longitude, place.latitude, place.longitude)
            else Double.NaN
            PlaceDetailSheet().apply {
                arguments = Bundle().apply {
                    putLong(ID, place.id)
                    putString(NAME, place.name)
                    putString(ADDRESS, place.address)
                    putString(HOURS, place.openingHours)
                    putString(PHONE, place.phone)
                    putString(BOOKING, place.bookingUrl)
                    putDouble(LAT, place.latitude ?: Double.NaN)
                    putDouble(LON, place.longitude ?: Double.NaN)
                    putDouble(DISTANCE, distance)
                }
            }.show(manager, "place_detail")
        }
    }
}
