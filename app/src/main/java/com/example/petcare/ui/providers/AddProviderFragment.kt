package com.example.petcare.ui.providers

import com.example.petcare.ui.UiSnackbar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.design.applySystemBarTopPadding
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.ui.SelectionDialog
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.data.local.provider.ProviderRepository
import com.example.petcare.databinding.FragmentAddProviderBinding
import kotlinx.coroutines.launch

/** Creates or edits a saved place, with coordinates from the map picker. */
class AddProviderFragment : Fragment() {
    private var _binding: FragmentAddProviderBinding? = null
    private val binding get() = _binding!!
    private val repositories by lazy { PetCareRepositories(requireContext()) }
    private var selectedType = "Veterinary clinic"
    private var editing: ProviderEntity? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = FragmentAddProviderBinding.inflate(inflater, container, false).also { _binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        view.applySystemBarTopPadding()
        val types = resources.getStringArray(R.array.provider_types).toList()
        binding.typeInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, types)); binding.typeInput.setText(selectedType, false)
        binding.typeInput.setOnItemClickListener { _, _, position, _ -> selectedType = types[position] }
        SelectionDialog.attach(binding.typeInput, R.string.provider_type, types) {
            selectedType = types[it]
        }
        parentFragmentManager.setFragmentResultListener(PlacePickerFragment.PIN_RESULT, viewLifecycleOwner) { _, result ->
            binding.latitudeInput.setText(coordinate(result.getDouble(PlacePickerFragment.LATITUDE)))
            binding.longitudeInput.setText(coordinate(result.getDouble(PlacePickerFragment.LONGITUDE)))
            result.getString(PlacePickerFragment.ADDRESS)?.takeIf(String::isNotBlank)?.let {
                binding.addressInput.setText(it)
            }
        }
        binding.pickOnMapButton.setOnClickListener {
            findNavController().navigate(R.id.action_add_provider_to_picker)
        }
        val editId = arguments?.getLong(ProviderListFragment.PLACE_ID) ?: 0L
        if (editId > 0L) viewLifecycleOwner.lifecycleScope.launch {
            editing = repositories.places.get(editId)
            editing?.let { place ->
                binding.nameInput.setText(place.name)
                selectedType = place.type
                binding.typeInput.setText(place.type, false)
                binding.addressInput.setText(place.address)
                place.latitude?.let { binding.latitudeInput.setText(coordinate(it)) }
                place.longitude?.let { binding.longitudeInput.setText(coordinate(it)) }
                binding.hoursInput.setText(place.openingHours)
                binding.phoneInput.setText(place.phone)
                binding.urlInput.setText(place.bookingUrl)
                binding.saveButton.setText(R.string.save_changes)
            }
        }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }; binding.saveButton.setOnClickListener { save() }
    }
    private fun save() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty(); val address = binding.addressInput.text?.toString()?.trim().orEmpty()
        binding.nameLayout.error = if (name.isBlank()) getString(R.string.error_provider_name_required) else null
        binding.addressLayout.error = if (address.isBlank()) getString(R.string.error_provider_address_required) else null
        if (name.isBlank() || address.isBlank()) return
        val latText = binding.latitudeInput.text?.toString()?.trim().orEmpty()
        val lonText = binding.longitudeInput.text?.toString()?.trim().orEmpty()
        val latitude = latText.toDoubleOrNull()
        val longitude = lonText.toDoubleOrNull()
        if ((latText.isNotEmpty() || lonText.isNotEmpty()) &&
            (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0)) {
            com.example.petcare.ui.UiSnackbar.make(binding.root,
                R.string.place_coordinate_invalid, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            return
        }
        val item = ProviderEntity(id = editing?.id ?: 0L,
            ownerId = editing?.ownerId ?: repositories.ownerId,
            name = name, type = selectedType, address = address,
            latitude = latitude, longitude = longitude,
            openingHours = binding.hoursInput.text?.toString()?.trim().orEmpty(), phone = binding.phoneInput.text?.toString()?.trim().orEmpty(), bookingUrl = binding.urlInput.text?.toString()?.trim().orEmpty())
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = repositories.places
            if (editing == null) repository.add(item) else repository.update(item)
            findNavController().navigateUp()
        }
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
    /** Coordinate forms use a stable decimal dot so their parser works in every locale. */
    private fun coordinate(value: Double): String = java.math.BigDecimal.valueOf(value)
        .stripTrailingZeros().toPlainString()
}
