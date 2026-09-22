package com.example.petcare.ui.pets

import com.example.petcare.ui.UiSnackbar
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.data.local.pet.PetColorPicker
import com.example.petcare.databinding.FragmentAddPetBinding
import coil.load
import com.google.android.material.snackbar.Snackbar

/** Creates a pet profile with optional photos and a stable identity colour. */
class AddPetFragment : Fragment() {

    private var _binding: FragmentAddPetBinding? = null
    private val binding get() = _binding!!
    private val repositories by lazy { PetCareRepositories(requireContext()) }

    private val viewModel: AddPetViewModel by viewModels {
        AddPetViewModelFactory(
        repositories.pets
        )
    }
    private val selectedPhotoUris = mutableListOf<Uri>()
    private var selectedColorIndex: Int? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach {
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (it !in selectedPhotoUris) selectedPhotoUris += it
        }
        selectedPhotoUris.firstOrNull()?.let {
            binding.petPhotoPreview.visibility = View.VISIBLE
            binding.petPhotoPreview.setPadding(0, 0, 0, 0)
            binding.petPhotoPreview.load(it)
        }
        updatePhotoCount()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.savePetButton.setOnClickListener {
            if (isValidInput()) {
                savePet()
            }
        }
        binding.selectPhotoButton.setOnClickListener { photoPicker.launch(arrayOf("image/*")) }
        PetColorPicker.bind(binding.petColorGroup, requireContext(), null) {
            selectedColorIndex = it
        }

        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun isValidInput(): Boolean {
        val name = binding.petNameInput.text?.toString()?.trim().orEmpty()
        val species = binding.petSpeciesInput.text?.toString()?.trim().orEmpty()

        binding.petNameLayout.error = if (name.isEmpty()) {
            getString(R.string.error_pet_name_required)
        } else {
            null
        }
        binding.petSpeciesLayout.error = if (species.isEmpty()) {
            getString(R.string.error_pet_species_required)
        } else {
            null
        }

        return binding.petNameLayout.error == null && binding.petSpeciesLayout.error == null
    }

    private fun savePet() {
        binding.savePetButton.isEnabled = false
        viewModel.addPet(
            name = binding.petNameInput.text?.toString()?.trim().orEmpty(),
            species = binding.petSpeciesInput.text?.toString()?.trim().orEmpty(),
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
            photoUris = selectedPhotoUris.map(Uri::toString),
            selectedColorIndex = selectedColorIndex,
            onError = {
                binding.savePetButton.isEnabled = true
                UiSnackbar.make(binding.root, R.string.pet_save_failed, Snackbar.LENGTH_LONG).show()
            },
            onSaved = { _ ->
                findNavController().navigateUp()
                UiSnackbar.make(requireActivity().findViewById(android.R.id.content),
                    R.string.pet_saved, Snackbar.LENGTH_SHORT).show()
            }
        )
    }

    private fun updatePhotoCount() {
        binding.photoCountText.text = if (selectedPhotoUris.isEmpty()) {
            getString(R.string.profile_photo_subtitle)
        } else {
            resources.getQuantityString(
                R.plurals.selected_photo_count,
                selectedPhotoUris.size,
                selectedPhotoUris.size
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
