package com.example.petcare.ui.pets

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.petcare.R
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.databinding.FragmentPetDetailBinding
import com.example.petcare.ui.MotionPrefs
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import kotlinx.coroutines.launch

/** Pet profile entered through a shared identity card transform. */
class PetDetailFragment : Fragment() {
    private var _binding: FragmentPetDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (MotionPrefs.animationsEnabled(requireContext())) {
            sharedElementEnterTransition = MaterialContainerTransform().apply {
                drawingViewId = R.id.nav_host_fragment
                duration = 450L
                scrimColor = Color.TRANSPARENT
            }
            postponeEnterTransition()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentPetDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val petId = requireArguments().getLong("petId")
        binding.petDetailCard.transitionName = "pet_$petId"
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.editPetButton.setOnClickListener {
            findNavController().navigate(R.id.action_pet_detail_to_edit, Bundle().apply {
                putLong("petId", petId)
            })
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val pet = PetRepository(PetCareDatabase.getInstance(requireContext()).petDao())
                .getPet(petId)
            if (pet == null) {
                startPostponedEnterTransition()
                Snackbar.make(requireActivity().findViewById(R.id.main),
                    R.string.pet_unavailable, Snackbar.LENGTH_LONG).show()
                findNavController().navigate(R.id.petListFragment, null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.petDetailFragment, true).build())
                return@launch
            }
            binding.petName.text = pet.name
            binding.petSpecies.text = listOf(pet.species, pet.breed, pet.age)
                .filter(String::isNotBlank).joinToString(" • ")
            binding.petColorRail.setBackgroundColor(PetColor.fromIndex(pet.colorIndex).primary)
            binding.petHealth.text = pet.healthNotes
            binding.petHealth.visibility = if (pet.healthNotes.isBlank()) View.GONE else View.VISIBLE
            binding.petAllergies.text = getString(R.string.pet_allergies_summary, pet.allergies)
            binding.petAllergies.visibility = if (pet.allergies.isBlank()) View.GONE else View.VISIBLE
            pet.photos().firstOrNull()?.let {
                binding.petPhoto.setPadding(0, 0, 0, 0)
                binding.petPhoto.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.petPhoto.load(it)
            }
            startPostponedEnterTransition()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
