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
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.data.local.pet.PetColorPicker
import com.example.petcare.databinding.FragmentAddPetBinding
import kotlinx.coroutines.launch

/** Updates an existing pet profile and its photos without changing its owner. */
class EditPetFragment : Fragment() {
    private var _binding: FragmentAddPetBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { PetCareRepositories(requireContext()).pets }
    private var pet: PetEntity? = null
    private var selectedColorIndex = 0
    private val photoUris = mutableListOf<Uri>()
    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach {
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (it !in photoUris) photoUris += it
        }
        photoUris.firstOrNull()?.let {
            binding.petPhotoPreview.visibility = View.VISIBLE
            binding.petPhotoPreview.setPadding(0, 0, 0, 0)
            binding.petPhotoPreview.load(it)
        }
        updatePhotoCount()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddPetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val id = arguments?.getLong("petId") ?: 0L
        if (id == 0L) { findNavController().navigateUp(); return }
        binding.petFormTitle.setText(R.string.edit_pet_title)
        binding.petFormSubtitle.setText(R.string.edit_pet_subtitle)
        binding.savePetButton.setText(R.string.save_changes)
        binding.selectPhotoButton.setOnClickListener { photoPicker.launch(arrayOf("image/*")) }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
        binding.savePetButton.setOnClickListener { save() }
        viewLifecycleOwner.lifecycleScope.launch {
            pet = repository.getPet(id)
            val savedPet = pet ?: run { findNavController().navigateUp(); return@launch }
            binding.petNameInput.setText(savedPet.name)
            binding.petSpeciesInput.setText(savedPet.species)
            selectedColorIndex = savedPet.colorIndex
            PetColorPicker.bind(binding.petColorGroup, requireContext(), selectedColorIndex) {
                selectedColorIndex = it
            }
            binding.petBreedInput.setText(savedPet.breed)
            binding.petAgeInput.setText(savedPet.age)
            binding.petWeightInput.setText(savedPet.weight)
            binding.dietaryPreferencesInput.setText(savedPet.dietaryPreferences)
            binding.vaccinationHistoryInput.setText(savedPet.vaccinationHistory)
            binding.allergiesInput.setText(savedPet.allergies)
            binding.favoriteToysInput.setText(savedPet.favoriteToys)
            binding.medicalRecordsInput.setText(savedPet.medicalRecords)
            binding.groomingRoutineInput.setText(savedPet.groomingRoutine)
            binding.healthNotesInput.setText(savedPet.healthNotes)
            photoUris.clear()
            photoUris += savedPet.photos().map(Uri::parse)
            photoUris.firstOrNull()?.let { uri ->
                binding.petPhotoPreview.visibility = View.VISIBLE
                binding.petPhotoPreview.setPadding(0, 0, 0, 0)
                binding.petPhotoPreview.load(uri)
            }
            updatePhotoCount()
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
            val storedPhotos = photoUris.map(Uri::toString)
            repository.updatePet(savedPet.copy(
                name = name,
                species = species,
                colorIndex = selectedColorIndex,
                breed = binding.petBreedInput.text?.toString()?.trim().orEmpty(),
                age = binding.petAgeInput.text?.toString()?.trim().orEmpty(),
                weight = binding.petWeightInput.text?.toString()?.trim().orEmpty(),
                dietaryPreferences = binding.dietaryPreferencesInput.text?.toString()?.trim().orEmpty(),
                vaccinationHistory = binding.vaccinationHistoryInput.text?.toString()?.trim().orEmpty(),
                allergies = binding.allergiesInput.text?.toString()?.trim().orEmpty(),
                favoriteToys = binding.favoriteToysInput.text?.toString()?.trim().orEmpty(),
                medicalRecords = binding.medicalRecordsInput.text?.toString()?.trim().orEmpty(),
                groomingRoutine = binding.groomingRoutineInput.text?.toString()?.trim().orEmpty(),
                healthNotes = binding.healthNotesInput.text?.toString()?.trim().orEmpty(),
                photoUri = storedPhotos.firstOrNull(),
                photoUris = storedPhotos.joinToString(PetEntity.PHOTO_SEPARATOR)
            ))
            findNavController().navigateUp()
        }
    }

    private fun updatePhotoCount() {
        binding.photoCountText.text = if (photoUris.isEmpty()) {
            getString(R.string.profile_photo_subtitle)
        } else {
            resources.getQuantityString(R.plurals.selected_photo_count, photoUris.size, photoUris.size)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
