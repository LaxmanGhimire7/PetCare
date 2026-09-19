package com.example.petcare.ui.providers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.databinding.FragmentPlacePickerBinding
import com.example.petcare.location.PlaceLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch

/** Lets the user drop a pin and reverse-geocodes it before returning to the place form. */
class PlacePickerFragment : Fragment() {
    private var _binding: FragmentPlacePickerBinding? = null
    private val binding get() = _binding!!
    private var pin: LatLng? = null
    private var marker: Marker? = null
    private var address: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentPlacePickerBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        if (!PlaceLocation.mapsAvailable(requireContext())) {
            binding.pickerMapFrame.visibility = View.GONE
            binding.pickerStatus.setText(R.string.map_unavailable)
            return
        }
        val fragment = SupportMapFragment.newInstance()
        childFragmentManager.beginTransaction().replace(R.id.picker_map_frame, fragment).commitNow()
        fragment.getMapAsync { map ->
            if (_binding == null) return@getMapAsync
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0, 0.0), 2f))
            map.setOnMapLongClickListener { point ->
                pin = point
                marker?.remove()
                marker = map.addMarker(MarkerOptions().position(point))
                binding.usePinButton.isEnabled = true
                binding.pickerStatus.setText(R.string.pin_saved)
                // A later geocoder callback must not replace a newer selected pin.
                viewLifecycleOwner.lifecycleScope.launch {
                    val found = PlaceLocation.reverseGeocode(requireContext(), point.latitude, point.longitude)
                    if (_binding != null && pin == point) {
                        address = found
                        if (found == null) binding.pickerStatus.setText(R.string.geocode_unavailable)
                        else binding.pickerStatus.text = found
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

    override fun onDestroyView() { marker = null; _binding = null; super.onDestroyView() }

    companion object {
        const val PIN_RESULT = "selectedPlacePin"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val ADDRESS = "address"
    }
}
