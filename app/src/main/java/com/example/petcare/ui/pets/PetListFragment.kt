package com.example.petcare.ui.pets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.petcare.R
import com.example.petcare.data.local.care.CARE_FREQUENCY_DAILY
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.databinding.FragmentPetListBinding
import com.example.petcare.databinding.ItemPetProfileBinding
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.ui.RowMotion
import com.example.petcare.ui.ScreenState
import com.example.petcare.ui.UiSnackbar
import com.example.petcare.ui.home.LocalDayClock
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Shows pet identity cards, notable care coming up, and current-month spending. */
class PetListFragment : Fragment() {
    private var _binding: FragmentPetListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PetListViewModel by viewModels()
    private val adapter = PetAdapter()
    private var restoringId: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentPetListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.petRecycler.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.petRecycler.adapter = adapter
        val addPet = View.OnClickListener {
            findNavController().navigate(R.id.action_pets_to_add_pet)
        }
        binding.addPetButton.setOnClickListener(addPet)
        binding.addPetTile.setOnClickListener(addPet)
        binding.emptyAddPetButton.setOnClickListener(addPet)
        binding.monthCard.setOnClickListener {
            findNavController().navigate(R.id.expenseListFragment)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        ScreenState.Loading -> binding.emptyCard.visibility = View.GONE
                        ScreenState.Empty -> renderEmpty()
                        is ScreenState.Content -> {
                            adapter.submit(state.data)
                            renderSummarySections(state.data)
                            binding.emptyCard.visibility = View.GONE
                            binding.petRecycler.visibility = View.VISIBLE
                            binding.addPetTile.visibility = View.VISIBLE
                            showSummarySections(true)
                        }
                        is ScreenState.Error -> {
                            renderEmpty()
                            UiSnackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderEmpty() {
        adapter.submit(PetListUi(emptyList(), emptyList(), emptyList()))
        binding.emptyCard.visibility = View.VISIBLE
        binding.petRecycler.visibility = View.GONE
        binding.addPetTile.visibility = View.GONE
        showSummarySections(false)
    }

    /** Renders the notable seven-day care list and current-month account spending. */
    private fun renderSummarySections(ui: PetListUi) {
        val today = LocalDayClock.todayEpochDay()
        val notable = ui.tasks.asSequence()
            .filter { it.dueDateEpochDay in today..(today + 7L) }
            .filter(::isNotable)
            .sortedWith(compareBy(CareTaskSummary::dueDateEpochDay,
                CareTaskSummary::reminderMinutesOfDay))
            .take(MAX_COMING_UP)
            .toList()
        binding.comingUpText.text = if (notable.isEmpty()) {
            getString(R.string.pets_no_notable_tasks)
        } else notable.joinToString("\n") { task ->
            getString(R.string.pets_coming_up_row, formatDay(task.dueDateEpochDay),
                task.petName, task.title)
        }

        val monthly = ui.expenses.filter(::isCurrentMonth)
        binding.monthAmount.text = money(monthly.sumOf(ExpenseSummary::amountCents))
        binding.monthBreakdown.text = monthly.groupBy(ExpenseSummary::petName).entries
            .sortedBy { it.key }
            .joinToString(getString(R.string.pet_meta_separator)) { (name, rows) ->
                getString(R.string.pet_spent, name, money(rows.sumOf(ExpenseSummary::amountCents)))
            }.ifBlank { getString(R.string.expense_empty) }
    }

    private fun showSummarySections(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        binding.comingUpTitle.visibility = visibility
        binding.comingUpCard.visibility = visibility
        binding.monthTitle.visibility = visibility
        binding.monthCard.visibility = visibility
    }

    private fun isNotable(task: CareTaskSummary): Boolean {
        if (task.frequency == CARE_FREQUENCY_DAILY) return false
        val searchable = "${task.category} ${task.title}".lowercase(Locale.getDefault())
        return NOTABLE_TERMS.any(searchable::contains)
    }

    private fun formatDay(epochDay: Long): String =
        SimpleDateFormat("EEE d MMM", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochDay * MILLIS_PER_DAY))

    private fun money(cents: Long): String =
        NumberFormat.getCurrencyInstance().format(cents / 100.0)

    private fun isCurrentMonth(expense: ExpenseSummary): Boolean {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = expense.dateEpochDay * MILLIS_PER_DAY
        }
        return now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
            now.get(Calendar.MONTH) == date.get(Calendar.MONTH)
    }

    private fun edit(pet: PetEntity) {
        findNavController().navigate(R.id.action_pets_to_edit_pet, Bundle().apply {
            putLong("petId", pet.id)
        })
    }

    private fun openDetail(pet: PetEntity, card: View) {
        findNavController().navigate(R.id.action_pets_to_pet_detail, Bundle().apply {
            putLong("petId", pet.id)
        }, null, FragmentNavigatorExtras(card to "pet_${pet.id}"))
    }

    private fun delete(pet: PetEntity, row: View) {
        RowMotion.collapse(row) {
            viewLifecycleOwner.lifecycleScope.launch {
                val snapshot = viewModel.delete(pet.id) ?: return@launch
                val scheduler = CareReminderScheduler(requireContext())
                snapshot.tasks.forEach { scheduler.cancel(it.id) }
                UiSnackbar.make(binding.root, R.string.pet_deleted, Snackbar.LENGTH_LONG)
                    .setDuration(6000)
                    .setAction(R.string.undo) {
                        restoringId = pet.id
                        viewLifecycleOwner.lifecycleScope.launch {
                            viewModel.restore(snapshot)
                            snapshot.tasks.filterNot { it.isCompleted }.forEach(scheduler::schedule)
                        }
                    }.show()
            }
        }
    }

    override fun onDestroyView() {
        binding.petRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private inner class PetAdapter : RecyclerView.Adapter<PetAdapter.Holder>() {
        private var pets = emptyList<PetEntity>()
        private var progress = emptyMap<Long, Pair<Int, Int>>()

        fun submit(ui: PetListUi) {
            val items = ui.pets
            val before = pets
            val today = LocalDayClock.todayEpochDay()
            progress = ui.tasks.filter { it.dueDateEpochDay == today }.groupBy { it.petId }
                .mapValues { (_, tasks) -> tasks.count(CareTaskSummary::isCompleted) to tasks.size }
            val changes = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = before.size
                override fun getNewListSize() = items.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    before[oldItemPosition].id == items[newItemPosition].id
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    before[oldItemPosition] == items[newItemPosition]
            })
            pets = items
            changes.dispatchUpdatesTo(this)
            notifyItemRangeChanged(0, pets.size, PROGRESS_PAYLOAD)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemPetProfileBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount(): Int = pets.size

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(pets[position])

        inner class Holder(private val row: ItemPetProfileBinding) : RecyclerView.ViewHolder(row.root) {
            fun bind(pet: PetEntity) {
                row.root.visibility = View.VISIBLE
                row.root.layoutParams = row.root.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                val color = PetColor.fromIndex(pet.colorIndex)
                val (done, total) = progress[pet.id] ?: (0 to 0)
                row.petInitialText.setTextColor(color.onContainer(requireContext()))
                row.petNameText.text = pet.name
                row.petSpeciesText.text = listOf(pet.species, pet.breed)
                    .filter(String::isNotBlank)
                    .joinToString(getString(R.string.pet_meta_separator))
                row.petHealthNotesText.text = if (total == 0) {
                    getString(R.string.pet_no_tasks_today)
                } else getString(R.string.pet_tasks_progress, done, total)
                row.petProgressBadge.setPet(pet.name, pet.colorIndex, done, total)
                pet.photos().firstOrNull()?.let {
                    row.petInitialText.visibility = View.GONE
                    row.petPhotoImage.visibility = View.VISIBLE
                    row.petPhotoImage.setPadding(0, 0, 0, 0)
                    row.petPhotoImage.load(it)
                } ?: run {
                    row.petPhotoImage.visibility = View.GONE
                    row.petInitialText.visibility = View.VISIBLE
                    row.petInitialText.text = pet.name.trim().take(1).uppercase()
                    row.petInitialText.setBackgroundColor(color.container(requireContext()))
                }
                row.root.transitionName = "pet_${pet.id}"
                if (restoringId == pet.id) {
                    restoringId = null
                    RowMotion.expand(row.root)
                }
                row.root.setOnClickListener { openDetail(pet, row.root) }
                row.root.setOnLongClickListener {
                    PopupMenu(requireContext(), row.root).apply {
                        inflate(R.menu.pet_card_actions)
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_edit_pet -> { edit(pet); true }
                                R.id.action_remove_pet -> { delete(pet, row.root); true }
                                else -> false
                            }
                        }
                        show()
                    }
                    true
                }
            }
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val MAX_COMING_UP = 5
        const val PROGRESS_PAYLOAD = "progress"
        val NOTABLE_TERMS = listOf("vet", "groom", "vaccin", "medicat", "health", "flea")
    }
}
