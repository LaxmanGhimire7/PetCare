package com.example.petcare.ui.pets

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
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.databinding.FragmentAddPetBinding
import coil.load

class AddPetFragment : Fragment() {

    private var _binding: FragmentAddPetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddPetViewModel by viewModels {
        AddPetViewModelFactory(
            PetRepository(PetCareDatabase.getInstance(requireContext()).petDao())
        )
    }
    private var selectedPhotoUri: Uri? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selectedPhotoUri = it
            binding.petPhotoPreview.visibility = View.VISIBLE
            binding.petPhotoPreview.load(it)
        }
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
            healthNotes = binding.healthNotesInput.text?.toString()?.trim().orEmpty(),
            photoUri = selectedPhotoUri?.toString()
        ) { _ ->
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
