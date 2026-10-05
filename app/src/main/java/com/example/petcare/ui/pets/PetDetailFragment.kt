package com.example.petcare.ui.pets

import android.content.res.ColorStateList
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
import com.example.petcare.design.applySystemBarPaddingWithKeyboard
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.ui.expenses.ExpenseCategoryBars
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.databinding.FragmentPetDetailBinding
import com.example.petcare.databinding.ItemPetHeroBinding
import com.example.petcare.databinding.ItemPetDetailFieldBinding
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.ScreenState
import com.example.petcare.ui.UiSnackbar
import com.example.petcare.ui.home.LocalDayClock
import com.example.petcare.ui.integration.DelegationPreviewFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
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
    private var selectedTab = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentPetDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.petDetailSafeArea.applySystemBarPaddingWithKeyboard()
        currentPetId = state?.getLong(STATE_PET_ID) ?: requireArguments().getLong("petId")
        selectedTab = state?.getInt(STATE_TAB)?.coerceIn(0, 3) ?: 0
        binding.petDetailCard.transitionName = "pet_$currentPetId"
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.shareChecklistButton.setOnClickListener {
            val pet = currentPet ?: return@setOnClickListener
            val rows = petTasks(pet).filterNot { it.isCompleted }
                .sortedWith(compareBy(CareTaskSummary::dueDateEpochDay, CareTaskSummary::reminderMinutesOfDay))
            if (rows.isEmpty()) {
                UiSnackbar.make(view, R.string.nothing_to_share, Snackbar.LENGTH_SHORT).show()
            } else {
                val message = rows.joinToString("\n\n") { task ->
                    getString(R.string.task_share_body, pet.name, task.title,
                        date(task.dueDateEpochDay), time(task), task.notes)
                }
                findNavController().navigate(R.id.delegationPreviewFragment, Bundle().apply {
                    putString(DelegationPreviewFragment.MESSAGE, message)
                    putLongArray(DelegationPreviewFragment.PET_IDS, longArrayOf(pet.id))
                })
            }
        }
        binding.editPetButton.setOnClickListener {
            if (selectedTab == 3) {
                findNavController().navigate(R.id.action_pet_detail_to_expenses)
                return@setOnClickListener
            }
            findNavController().navigate(R.id.action_pet_detail_to_edit, Bundle().apply {
                putLong("petId", currentPetId)
                putString(PetFormSections.ARG_SECTION, when {
                    selectedTab == 1 -> PetFormSections.HEALTH
                    selectedTab == 0 -> PetFormSections.CARE
                    else -> PetFormSections.BASICS
                })
            })
        }
        binding.petSectionActionButton.setOnClickListener {
            when {
                selectedTab == 0 -> findNavController().navigate(
                    R.id.action_pet_detail_to_add_care_task,
                    Bundle().apply { putLong("preselectPetId", currentPetId) },
                )
                selectedTab == 3 -> findNavController().navigate(
                    R.id.action_pet_detail_to_add_expense,
                    Bundle().apply { putLong("petId", currentPetId) },
                )
            }
        }
        binding.petTabGroup.getTabAt(selectedTab)?.select()
        binding.petTabGroup.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTab = tab.position
                currentPet?.let(::renderSelectedTab)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
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
                val adapter = binding.petPager.adapter as? PetHeroAdapter
                if (idsChanged || adapter == null) configurePager(position)
                else adapter.updatePets(pets)
                showPet(pets[position], position)
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
        if (!isAdded || findNavController().currentDestination?.id != R.id.petDetailFragment) return
        if (showMessage) UiSnackbar.make(requireActivity().findViewById(R.id.main),
            R.string.pet_unavailable, Snackbar.LENGTH_LONG).show()
        findNavController().navigate(R.id.petListFragment, null,
            androidx.navigation.NavOptions.Builder().setPopUpTo(R.id.petDetailFragment, true).build())
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
        val care = selectedTab == 0
        val spending = selectedTab == 3
        binding.shareChecklistButton.visibility = if (care) View.VISIBLE else View.GONE
        binding.petSectionActionButton.visibility = if (care || spending) View.VISIBLE else View.GONE
        binding.editPetButton.visibility = View.VISIBLE
        when {
            selectedTab == 1 -> {
                binding.petSectionTitle.setText(R.string.pet_health_section_title)
                binding.petSectionIntro.setText(R.string.pet_health_section_intro)
                binding.editPetButton.setText(R.string.pet_edit_health)
                renderProfileFields(listOf(
                    R.string.vaccination_history to pet.vaccinationHistory,
                    R.string.allergies to pet.allergies,
                    R.string.medical_records to pet.medicalRecords,
                    R.string.health_notes to pet.healthNotes,
                ))
            }
            selectedTab == 2 -> {
                binding.petSectionTitle.setText(R.string.pet_basics_section_title)
                binding.petSectionIntro.setText(R.string.pet_basics_section_intro)
                binding.editPetButton.setText(R.string.pet_edit_basics)
                renderProfileFields(listOf(
                    R.string.pet_species to pet.species,
                    R.string.pet_breed to pet.breed,
                    R.string.pet_age to pet.age,
                    R.string.pet_weight to pet.weight,
                ))
            }
            spending -> {
                binding.petSectionTitle.setText(R.string.pet_spending_section_title)
                binding.petSectionIntro.setText(R.string.pet_spending_section_intro)
                binding.petSectionActionButton.setText(R.string.pet_add_expense)
                binding.editPetButton.setText(R.string.pet_manage_expenses)
                renderSpending(pet)
            }
            else -> {
                binding.petSectionTitle.setText(R.string.pet_care_section_title)
                binding.petSectionIntro.setText(R.string.pet_care_section_intro)
                binding.petSectionActionButton.setText(R.string.pet_add_care_task)
                binding.editPetButton.setText(R.string.pet_edit_care)
                renderCare(pet)
            }
        }
        binding.petSectionFields.post { binding.petSectionFields.requestLayout() }
    }

    private fun renderProfileFields(fields: List<Pair<Int, String>>) {
        renderRows(fields.map { getString(it.first) to it.second })
    }

    private fun renderRows(rows: List<Pair<String, String>>) {
        binding.petSectionFields.removeAllViews()
        renderRowsAfter(rows)
    }

    private fun renderCare(pet: PetEntity) {
        val today = LocalDayClock.todayEpochDay()
        val tasks = petTasks(pet)
        val todayTasks = tasks.filter { it.dueDateEpochDay == today }
            .sortedBy(CareTaskSummary::reminderMinutesOfDay)
        val comingTasks = tasks.filter { it.dueDateEpochDay in (today + 1L)..(today + 7L) }
            .sortedWith(compareBy(CareTaskSummary::dueDateEpochDay,
                CareTaskSummary::reminderMinutesOfDay)).take(5)
        renderRows(listOf(
            getString(R.string.pet_care_today_label) to if (todayTasks.isEmpty())
                getString(R.string.pet_care_empty_today) else getString(R.string.pet_task_tap_to_edit),
        ))
        todayTasks.forEach(::addTaskRow)
        renderRowsAfter(listOf(
            getString(R.string.pet_care_upcoming_label) to if (comingTasks.isEmpty())
                getString(R.string.pet_care_empty_upcoming) else getString(R.string.pet_task_tap_to_edit),
        ))
        comingTasks.forEach(::addTaskRow)
        renderRowsAfter(listOf(
            getString(R.string.dietary_preferences) to pet.dietaryPreferences,
            getString(R.string.grooming_routine) to pet.groomingRoutine,
            getString(R.string.favorite_toys) to pet.favoriteToys,
        ))
    }

    private fun renderRowsAfter(rows: List<Pair<String, String>>) {
        rows.forEach { (label, value) ->
            val row = ItemPetDetailFieldBinding.inflate(layoutInflater, binding.petSectionFields, false)
            row.fieldLabel.text = label
            row.fieldValue.text = value.ifBlank { getString(R.string.pet_field_not_added) }
            binding.petSectionFields.addView(row.root)
        }
    }

    private fun addTaskRow(task: CareTaskSummary) {
        val row = ItemPetDetailFieldBinding.inflate(layoutInflater, binding.petSectionFields, false)
        row.fieldLabel.text = getString(R.string.pet_care_task_edit_label,
            date(task.dueDateEpochDay), time(task))
        row.fieldValue.text = getString(R.string.pet_care_task_edit_value, task.title,
            getString(if (task.isCompleted) R.string.pet_care_done else R.string.pet_care_open))
        row.root.isFocusable = true
        row.root.setOnClickListener {
            findNavController().navigate(R.id.action_pet_detail_to_edit_care_task,
                Bundle().apply { putLong("careTaskId", task.id) })
        }
        binding.petSectionFields.addView(row.root)
    }

    private fun renderSpending(pet: PetEntity) {
        val monthly = detail.expenses.filter { it.petId == pet.id && isCurrentMonth(it) }
        val total = monthly.sumOf(ExpenseSummary::amountCents)
        renderRows(listOf(getString(R.string.pet_spending_month_label) to money(total)))
        if (monthly.isEmpty()) {
            renderRowsAfter(listOf(getString(R.string.pet_spending_categories_label) to
                getString(R.string.money_no_expenses_month)))
        } else {
            ExpenseCategoryBars.append(binding.petSectionFields, layoutInflater, monthly, ::money)
        }
        monthly.sortedByDescending(ExpenseSummary::dateEpochDay).take(5).forEach { expense ->
            val row = ItemPetDetailFieldBinding.inflate(layoutInflater, binding.petSectionFields, false)
            row.fieldLabel.text = getString(R.string.pet_expense_edit_label, date(expense.dateEpochDay))
            row.fieldValue.text = getString(R.string.pet_expense_edit_value,
                expense.note.ifBlank { expense.category }, money(expense.amountCents))
            row.root.isFocusable = true
            row.root.setOnClickListener {
                findNavController().navigate(R.id.action_pet_detail_to_add_expense,
                    Bundle().apply { putLong("expenseId", expense.id) })
            }
            binding.petSectionFields.addView(row.root)
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_TAB, selectedTab)
        outState.putLong(STATE_PET_ID, currentPetId)
        super.onSaveInstanceState(outState)
    }

    /** Displays one pet hero and opens the existing full-screen zoom viewer for real photos. */
    private class PetHeroAdapter(
        private var pets: List<PetEntity>, private val open: (PetEntity) -> Unit
    ) : RecyclerView.Adapter<PetHeroAdapter.Holder>() {
        fun updatePets(updated: List<PetEntity>) {
            val previous = pets
            pets = updated
            updated.forEachIndexed { index, pet ->
                if (pet != previous[index]) notifyItemChanged(index)
            }
        }

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
        const val STATE_TAB = "selectedTab"
        const val STATE_PET_ID = "selectedPetId"
    }
}
