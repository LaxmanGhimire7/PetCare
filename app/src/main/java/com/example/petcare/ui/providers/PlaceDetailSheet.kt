package com.example.petcare.ui.providers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.example.petcare.R
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.databinding.SheetPlaceDetailBinding
import com.example.petcare.location.PlaceLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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
        if (lat.isFinite() && lon.isFinite() && PlaceLocation.mapsAvailable(requireContext())) {
            val mapView = MapView(requireContext(), GoogleMapOptions().liteMode(true))
            preview = mapView
            binding.placePreviewFrame.addView(mapView)
            mapView.onCreate(state)
            mapView.getMapAsync { map ->
                val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
                map.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(),
                    if (night) R.raw.map_style_dark else R.raw.map_style_light))
                val point = LatLng(lat, lon)
                map.addMarker(MarkerOptions().position(point).title(name))
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 14f))
            }
        } else {
            binding.placePreviewFrame.visibility = View.GONE
            binding.placePreviewFallback.visibility = View.VISIBLE
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
