package com.example.petcare.ui.pets

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.example.petcare.R
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.databinding.FragmentPetDetailBinding
import com.example.petcare.databinding.ItemPetHeroBinding
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.MotionPrefs
import com.example.petcare.ui.ScreenState
import com.example.petcare.ui.UiSnackbar
import com.example.petcare.ui.home.LocalDayClock
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Swipeable pet profile whose tabs stay synchronized with task and expense Room flows. */
class PetDetailFragment : Fragment() {
    private var _binding: FragmentPetDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PetDetailViewModel by viewModels()
    private var pets = emptyList<PetEntity>()
    private var detail = PetDetailUi(emptyList(), emptyList(), emptyList())
    private var currentPetId = 0L
    private var currentPet: PetEntity? = null
    private var pageCallback: ViewPager2.OnPageChangeCallback? = null
    private var transitionFinished = false

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
        currentPetId = requireArguments().getLong("petId")
        binding.petDetailCard.transitionName = "pet_$currentPetId"
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.editPetButton.setOnClickListener {
            findNavController().navigate(R.id.action_pet_detail_to_edit, Bundle().apply {
                putLong("petId", currentPetId)
            })
        }
        listOf(binding.petTabCare, binding.petTabHealth, binding.petTabProfile,
            binding.petTabSpending).forEach { tab ->
            tab.setOnClickListener { currentPet?.let(::renderSelectedTab) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::renderState)
            }
        }
    }

    private fun renderState(state: ScreenState<PetDetailUi>) {
        when (state) {
            ScreenState.Loading -> Unit
            ScreenState.Empty -> leaveUnavailable()
            is ScreenState.Error -> {
                UiSnackbar.make(requireActivity().findViewById(R.id.main),
                    state.message, Snackbar.LENGTH_LONG).show()
                leaveUnavailable(showMessage = false)
            }
            is ScreenState.Content -> {
                detail = state.data
                val idsChanged = pets.map(PetEntity::id) != state.data.pets.map(PetEntity::id)
                pets = state.data.pets
                val position = pets.indexOfFirst { it.id == currentPetId }
                if (position < 0) {
                    leaveUnavailable()
                    return
                }
                if (idsChanged || binding.petPager.adapter == null) configurePager(position)
                showPet(pets[position], position)
                finishTransition()
            }
        }
    }

    private fun configurePager(position: Int) {
        binding.petPager.adapter = PetHeroAdapter(pets, ::openPhotos)
        if (pageCallback == null) {
            var previousPage = -1
            pageCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(page: Int) {
                    if (previousPage >= 0 && previousPage != page) GestureHaptics.confirm(binding.petPager)
                    previousPage = page
                    pets.getOrNull(page)?.let { showPet(it, page) }
                }
            }.also(binding.petPager::registerOnPageChangeCallback)
        }
        binding.petPager.setCurrentItem(position, false)
    }

    private fun leaveUnavailable(showMessage: Boolean = true) {
        finishTransition()
        if (!isAdded || findNavController().currentDestination?.id != R.id.petDetailFragment) return
        if (showMessage) UiSnackbar.make(requireActivity().findViewById(R.id.main),
            R.string.pet_unavailable, Snackbar.LENGTH_LONG).show()
        findNavController().navigate(R.id.petListFragment, null,
            androidx.navigation.NavOptions.Builder().setPopUpTo(R.id.petDetailFragment, true).build())
    }

    private fun finishTransition() {
        if (!transitionFinished) {
            transitionFinished = true
            startPostponedEnterTransition()
        }
    }

    private fun showPet(pet: PetEntity, position: Int) {
        currentPetId = pet.id
        currentPet = pet
        binding.petDetailCard.transitionName = "pet_${pet.id}"
        binding.petPageCount.text = getString(R.string.pet_page_count, position + 1, pets.size)
        binding.petPageCount.visibility = if (pets.size > 1) View.VISIBLE else View.GONE
        binding.petName.text = pet.name
        binding.petSpecies.text = listOf(pet.species, pet.breed, pet.age)
            .filter(String::isNotBlank).joinToString(getString(R.string.pet_meta_separator))
        binding.petColorRail.setBackgroundColor(PetColor.fromIndex(pet.colorIndex).color(requireContext()))
        binding.petAgeStat.text = pet.age.ifBlank { getString(R.string.value_not_set) }
        binding.petWeightStat.text = pet.weight.ifBlank { getString(R.string.value_not_set) }
        val todayTasks = petTasks(pet).filter { it.dueDateEpochDay == LocalDayClock.todayEpochDay() }
        binding.petDoneStat.text = if (todayTasks.isEmpty()) getString(R.string.value_not_set)
        else getString(R.string.pet_done_figure, todayTasks.count(CareTaskSummary::isCompleted), todayTasks.size)
        renderSelectedTab(pet)
    }

    /** Converts live Room data into the selected Care, Health, Profile, or Spending tab. */
    private fun renderSelectedTab(pet: PetEntity) {
        when {
            binding.petTabHealth.isChecked -> renderProfileFields(listOf(
                R.string.vaccination_history to pet.vaccinationHistory,
                R.string.allergies to pet.allergies,
                R.string.medical_records to pet.medicalRecords
            ))
            binding.petTabProfile.isChecked -> renderProfileFields(listOf(
                R.string.dietary_preferences to pet.dietaryPreferences,
                R.string.favorite_toys to pet.favoriteToys,
                R.string.grooming_routine to pet.groomingRoutine,
                R.string.health_notes to pet.healthNotes
            ))
            binding.petTabSpending.isChecked -> renderSpending(pet)
            else -> renderCare(pet)
        }
    }

    private fun renderProfileFields(fields: List<Pair<Int, String>>) {
        val lines = fields.filter { it.second.isNotBlank() }.map {
            getString(R.string.pet_detail_field, getString(it.first), it.second)
        }
        binding.petHealth.text = lines.firstOrNull() ?: getString(R.string.pet_profile_empty)
        binding.petAllergies.text = lines.drop(1).joinToString("\n\n")
        binding.petAllergies.visibility = if (lines.size > 1) View.VISIBLE else View.GONE
    }

    private fun renderCare(pet: PetEntity) {
        val today = LocalDayClock.todayEpochDay()
        val tasks = petTasks(pet)
        val todayLines = tasks.filter { it.dueDateEpochDay == today }
            .sortedBy(CareTaskSummary::reminderMinutesOfDay)
            .map { task ->
                getString(R.string.pet_care_task_line, time(task), task.title,
                    getString(if (task.isCompleted) R.string.pet_care_done else R.string.pet_care_open))
            }
        binding.petHealth.text = todayLines.joinToString("\n").ifBlank {
            getString(R.string.pet_care_empty_today)
        }
        val coming = tasks.filter { it.dueDateEpochDay in (today + 1L)..(today + 7L) }
            .sortedWith(compareBy(CareTaskSummary::dueDateEpochDay,
                CareTaskSummary::reminderMinutesOfDay)).take(5)
            .map { getString(R.string.pet_care_upcoming_line, date(it.dueDateEpochDay), it.title) }
        binding.petAllergies.text = if (coming.isEmpty()) getString(R.string.pet_care_empty_upcoming)
        else getString(R.string.pet_care_coming_up, coming.joinToString("\n"))
        binding.petAllergies.visibility = View.VISIBLE
    }

    private fun renderSpending(pet: PetEntity) {
        val monthly = detail.expenses.filter { it.petId == pet.id && isCurrentMonth(it) }
        val total = monthly.sumOf(ExpenseSummary::amountCents)
        binding.petHealth.text = getString(R.string.pet_spending_month, money(total))
        binding.petAllergies.text = monthly.groupBy(ExpenseSummary::category).entries
            .sortedByDescending { it.value.sumOf(ExpenseSummary::amountCents) }
            .joinToString("\n") { (category, rows) ->
                getString(R.string.pet_spending_category, category,
                    money(rows.sumOf(ExpenseSummary::amountCents)))
            }.ifBlank { getString(R.string.expense_empty) }
        binding.petAllergies.visibility = View.VISIBLE
    }

    private fun petTasks(pet: PetEntity) = detail.tasks.filter { it.petId == pet.id }

    private fun time(task: CareTaskSummary): String = DateFormat.getTimeInstance(DateFormat.SHORT)
        .format(Date(LocalDayClock.dueMillis(task.dueDateEpochDay, task.reminderMinutesOfDay)))

    private fun date(day: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(day * DAY))

    private fun money(cents: Long) = NumberFormat.getCurrencyInstance().format(cents / 100.0)

    private fun isCurrentMonth(expense: ExpenseSummary): Boolean {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val value = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = expense.dateEpochDay * DAY
        }
        return now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
            now.get(Calendar.MONTH) == value.get(Calendar.MONTH)
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

    /** Displays one pet hero and opens the existing full-screen zoom viewer for real photos. */
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
                image.imageTintList = ColorStateList.valueOf(
                    PetColor.fromIndex(pet.colorIndex).color(image.context))
                val padding = image.resources.getDimensionPixelSize(R.dimen.space_32)
                image.setPadding(padding, padding, padding, padding)
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

        /** Holds one pet's photo and profile hero card. */
        class Holder(val binding: ItemPetHeroBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
