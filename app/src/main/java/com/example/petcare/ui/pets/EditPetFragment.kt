package com.example.petcare.ui.pets

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.data.local.pet.PetColorPicker
import com.example.petcare.databinding.FragmentAddPetBinding
import com.example.petcare.design.applySystemBarPaddingWithKeyboard
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
        if (uris.isEmpty()) return@registerForActivityResult
        // Keep saved photos in place when the owner adds another picker selection.
        val updated = PetPhotoSelection.append(photoUris, uris)
        val added = updated.drop(photoUris.size)
        added.forEach {
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        photoUris.clear()
        photoUris += updated
        if (uris.distinct().count { it !in updated } > 0) {
            Toast.makeText(requireContext(), R.string.pet_photo_limit_reached, Toast.LENGTH_SHORT).show()
        }
        renderPhotos()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddPetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.applySystemBarPaddingWithKeyboard()
        PetFormSections.bind(binding, arguments?.getString(PetFormSections.ARG_SECTION).orEmpty())
        val id = arguments?.getLong("petId") ?: 0L
        if (id == 0L) { findNavController().navigateUp(); return }
        binding.petFormTitle.setText(R.string.edit_pet_title)
        binding.petFormSubtitle.setText(R.string.edit_pet_subtitle)
        binding.savePetButton.setText(R.string.save_changes)
        binding.savePetButton.isEnabled = false
        binding.selectPhotoButton.isEnabled = false
        binding.selectPhotoButton.setOnClickListener { photoPicker.launch(arrayOf("image/*")) }
        binding.clearPhotosButton.setOnClickListener {
            photoUris.clear()
            renderPhotos()
        }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
        binding.savePetButton.setOnClickListener { save() }
        viewLifecycleOwner.lifecycleScope.launch {
            pet = repository.getPet(id)
            val savedPet = pet ?: run { findNavController().navigateUp(); return@launch }
            binding.petNameInput.setText(savedPet.name)
            binding.petSpeciesInput.setText(savedPet.species)
            configureSpecies(savedPet.species)
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
            photoUris += if (savedInstanceState?.containsKey(PHOTO_STATE) == true) {
                savedInstanceState.getStringArrayList(PHOTO_STATE).orEmpty().map(Uri::parse)
            } else savedPet.photos().map(Uri::parse)
            renderPhotos()
            binding.savePetButton.isEnabled = true
            binding.selectPhotoButton.isEnabled = true
        }
    }

    private fun configureSpecies(initial: String) {
        binding.petSpeciesGroup.check(
            when (initial.lowercase()) {
                "cat" -> R.id.pet_species_cat
                "dog" -> R.id.pet_species_dog
                else -> R.id.pet_species_other
            },
        )
        binding.petSpeciesGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            binding.petSpeciesInput.setText(
                when (checkedIds.firstOrNull()) {
                    R.id.pet_species_cat -> "Cat"
                    R.id.pet_species_dog -> "Dog"
                    else -> "Other"
                },
            )
        }
    }

    private fun save() {
        val savedPet = pet ?: return
        val name = binding.petNameInput.text?.toString()?.trim().orEmpty()
        val species = binding.petSpeciesInput.text?.toString()?.trim().orEmpty()
        binding.petNameLayout.error = if (name.isEmpty()) getString(R.string.error_pet_name_required) else null
        binding.petSpeciesLayout.error = if (species.isEmpty()) getString(R.string.error_pet_species_required) else null
        if (name.isEmpty() || species.isEmpty()) {
            PetFormSections.show(binding, PetFormSections.BASICS)
            return
        }
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
            Toast.makeText(requireContext(), R.string.pet_updated, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun renderPhotos() {
        binding.selectPhotoButton.isEnabled = photoUris.size < PetPhotoSelection.MAX_PHOTOS
        PetPhotoStrip.render(binding, photoUris, onCoverSelected = { cover ->
            if (photoUris.firstOrNull() != cover) {
                val updated = PetPhotoSelection.chooseCover(photoUris, cover)
                photoUris.clear()
                photoUris += updated
                renderPhotos()
                binding.photoStripScroll.scrollTo(0, 0)
            }
        }, onPhotoRemoved = { photo ->
            photoUris.remove(photo)
            renderPhotos()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Before the profile loads, Room remains the source of truth after recreation.
        if (pet != null) outState.putStringArrayList(PHOTO_STATE, ArrayList(photoUris.map(Uri::toString)))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private companion object { const val PHOTO_STATE = "selectedPetPhotos" }
}
