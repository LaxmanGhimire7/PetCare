package com.example.petcare.ui.pets

import com.example.petcare.ui.UiSnackbar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.petcare.R
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.ui.ScreenState
import com.example.petcare.databinding.FragmentPetListBinding
import com.example.petcare.databinding.ItemPetProfileBinding
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.ui.RowMotion
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.widget.PopupMenu
import kotlinx.coroutines.launch

/** Primary list of pets; identity rails match the same pet's tasks and expenses. */
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        ScreenState.Loading -> binding.emptyCard.visibility = View.GONE
                        ScreenState.Empty -> {
                            adapter.submit(emptyList())
                            binding.emptyCard.visibility = View.VISIBLE
                            binding.petRecycler.visibility = View.GONE
                            binding.addPetTile.visibility = View.GONE
                        }
                        is ScreenState.Content -> {
                            adapter.submit(state.data)
                            binding.emptyCard.visibility = View.GONE
                            binding.petRecycler.visibility = View.VISIBLE
                            binding.addPetTile.visibility = View.VISIBLE
                        }
                        is ScreenState.Error -> {
                            adapter.submit(emptyList())
                            binding.emptyCard.visibility = View.VISIBLE
                            UiSnackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
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
        RowMotion.collapse(row) { viewLifecycleOwner.lifecycleScope.launch {
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
        } }
    }

    override fun onDestroyView() {
        binding.petRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private inner class PetAdapter : RecyclerView.Adapter<PetAdapter.Holder>() {
        private var pets = emptyList<PetEntity>()
        fun submit(items: List<PetEntity>) {
            val before = pets
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
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemPetProfileBinding.inflate(layoutInflater, parent, false))
        override fun getItemCount(): Int = pets.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(pets[position])
        }
        inner class Holder(private val row: ItemPetProfileBinding) : RecyclerView.ViewHolder(row.root) {
            fun bind(pet: PetEntity) {
                row.root.visibility = View.VISIBLE
                row.root.layoutParams = row.root.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                val color = PetColor.fromIndex(pet.colorIndex)
                row.petInitialText.setTextColor(color.onContainer(requireContext()))
                row.petNameText.text = pet.name
                row.petSpeciesText.text = listOf(pet.species, pet.breed)
                    .filter(String::isNotBlank).joinToString(" · ")
                row.petHealthNotesText.setText(R.string.pet_no_tasks_today)
                row.petProgressBadge.setPet(pet.name, pet.colorIndex, 0, 0)
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
}
