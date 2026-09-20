package com.example.petcare.ui.pets

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.example.petcare.R
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.databinding.FragmentPetDetailBinding
import com.example.petcare.databinding.ItemPetHeroBinding
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.MotionPrefs
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Pet profile whose hero pages move between animals while profile fields follow selection. */
class PetDetailFragment : Fragment() {
    private var _binding: FragmentPetDetailBinding? = null
    private val binding get() = _binding!!
    private var pets = emptyList<PetEntity>()
    private var currentPetId = 0L
    private var pageCallback: ViewPager2.OnPageChangeCallback? = null

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
        val requestedId = requireArguments().getLong("petId")
        currentPetId = requestedId
        binding.petDetailCard.transitionName = "pet_$requestedId"
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.editPetButton.setOnClickListener {
            findNavController().navigate(R.id.action_pet_detail_to_edit, Bundle().apply {
                putLong("petId", currentPetId)
            })
        }
        viewLifecycleOwner.lifecycleScope.launch {
            pets = PetRepository(PetCareDatabase.getInstance(requireContext()).petDao())
                .observePets().first()
            val start = pets.indexOfFirst { it.id == requestedId }
            if (start < 0) {
                startPostponedEnterTransition()
                Snackbar.make(requireActivity().findViewById(R.id.main),
                    R.string.pet_unavailable, Snackbar.LENGTH_LONG).show()
                findNavController().navigate(R.id.petListFragment, null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.petDetailFragment, true).build())
                return@launch
            }
            binding.petPager.adapter = PetHeroAdapter(pets, ::openPhotos)
            var previousPage = -1
            pageCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (previousPage >= 0 && previousPage != position)
                        GestureHaptics.confirm(binding.petPager)
                    previousPage = position
                    showPet(pets[position], position)
                }
            }.also(binding.petPager::registerOnPageChangeCallback)
            binding.petPager.setCurrentItem(start, false)
            showPet(pets[start], start)
            startPostponedEnterTransition()
        }
    }

    private fun showPet(pet: PetEntity, position: Int) {
        currentPetId = pet.id
        binding.petDetailCard.transitionName = "pet_${pet.id}"
        binding.petPageCount.text = getString(R.string.pet_page_count, position + 1, pets.size)
        binding.petPageCount.visibility = if (pets.size > 1) View.VISIBLE else View.GONE
        binding.petName.text = pet.name
        binding.petSpecies.text = listOf(pet.species, pet.breed, pet.age)
            .filter(String::isNotBlank).joinToString(getString(R.string.pet_meta_separator))
        binding.petColorRail.setBackgroundColor(PetColor.fromIndex(pet.colorIndex).primary)
        binding.petHealth.text = pet.healthNotes
        binding.petHealth.visibility = if (pet.healthNotes.isBlank()) View.GONE else View.VISIBLE
        binding.petAllergies.text = getString(R.string.pet_allergies_summary, pet.allergies)
        binding.petAllergies.visibility = if (pet.allergies.isBlank()) View.GONE else View.VISIBLE
    }

    private fun openPhotos(pet: PetEntity) {
        val photos = pet.photos()
        if (photos.isEmpty()) return
        GestureHaptics.confirm(binding.petPager)
        findNavController().navigate(R.id.action_pet_detail_to_photos, Bundle().apply {
            putStringArrayList(PhotoViewerFragment.PHOTO_URIS, ArrayList(photos))
            putInt(PhotoViewerFragment.START_INDEX, 0)
        })
    }

    override fun onDestroyView() {
        pageCallback?.let(binding.petPager::unregisterOnPageChangeCallback)
        pageCallback = null
        binding.petPager.adapter = null
        _binding = null
        super.onDestroyView()
    }

    /** The currently displayed hero photo is an entry point to that pet's whole gallery. */
    private class PetHeroAdapter(
        private val pets: List<PetEntity>, private val open: (PetEntity) -> Unit
    ) : RecyclerView.Adapter<PetHeroAdapter.Holder>() {
        override fun getItemCount() = pets.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            ItemPetHeroBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val pet = pets[position]
            val image = holder.binding.petHeroPhoto
            image.contentDescription = image.context.getString(R.string.pet_photo)
            val photo = pet.photos().firstOrNull()
            if (photo == null) {
                image.setImageResource(R.drawable.ic_pets)
                image.imageTintList = ColorStateList.valueOf(PetColor.fromIndex(pet.colorIndex).primary)
                image.setPadding(image.resources.getDimensionPixelSize(R.dimen.space_32),
                    image.resources.getDimensionPixelSize(R.dimen.space_32),
                    image.resources.getDimensionPixelSize(R.dimen.space_32),
                    image.resources.getDimensionPixelSize(R.dimen.space_32))
                image.scaleType = ImageView.ScaleType.FIT_CENTER
                image.setOnClickListener(null)
            } else {
                image.imageTintList = null
                image.setPadding(0, 0, 0, 0)
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.load(photo)
                image.setOnClickListener { open(pet) }
            }
        }
        class Holder(val binding: ItemPetHeroBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
