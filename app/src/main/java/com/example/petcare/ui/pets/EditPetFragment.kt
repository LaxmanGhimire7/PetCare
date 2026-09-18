package com.example.petcare.ui.pets

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.petcare.R
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.databinding.FragmentAddPetBinding
import kotlinx.coroutines.launch

class EditPetFragment : Fragment() {
    private var _binding: FragmentAddPetBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { PetRepository(PetCareDatabase.getInstance(requireContext()).petDao()) }
    private var pet: PetEntity? = null
    private var photoUri: Uri? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            photoUri = it
            binding.petPhotoPreview.visibility = View.VISIBLE
            binding.petPhotoPreview.load(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddPetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val id = arguments?.getLong("petId") ?: 0L
        if (id == 0L) { findNavController().navigateUp(); return }
        binding.savePetButton.setText(R.string.save_changes)
        binding.selectPhotoButton.setOnClickListener { photoPicker.launch(arrayOf("image/*")) }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
        binding.savePetButton.setOnClickListener { save() }
        viewLifecycleOwner.lifecycleScope.launch {
            pet = repository.getPet(id)
            val savedPet = pet ?: run { findNavController().navigateUp(); return@launch }
            binding.petNameInput.setText(savedPet.name)
            binding.petSpeciesInput.setText(savedPet.species)
            binding.healthNotesInput.setText(savedPet.healthNotes)
            savedPet.photoUri?.let { uri -> photoUri = Uri.parse(uri); binding.petPhotoPreview.visibility = View.VISIBLE; binding.petPhotoPreview.load(uri) }
        }
    }

    private fun save() {
        val savedPet = pet ?: return
        val name = binding.petNameInput.text?.toString()?.trim().orEmpty()
        val species = binding.petSpeciesInput.text?.toString()?.trim().orEmpty()
        binding.petNameLayout.error = if (name.isEmpty()) getString(R.string.error_pet_name_required) else null
        binding.petSpeciesLayout.error = if (species.isEmpty()) getString(R.string.error_pet_species_required) else null
        if (name.isEmpty() || species.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            repository.updatePet(savedPet.copy(name = name, species = species, healthNotes = binding.healthNotesInput.text?.toString()?.trim().orEmpty(), photoUri = photoUri?.toString()))
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
