package com.example.petcare.ui.pets

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.petcare.R
import com.example.petcare.design.PetsActions
import com.example.petcare.design.PetsScreenBinder
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.ui.ScreenState
import com.example.petcare.ui.UiSnackbar
import com.example.petcare.ui.PcTypography
import com.example.petcare.ui.home.TaskDetailSheet
import com.example.petcare.ui.toPetsUiState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Data and navigation for the unmodified design-kit pet grid. */
class PetListFragment : Fragment(R.layout.pc_fragment_pets), PetsActions {
    private val viewModel: PetListViewModel by viewModels()
    private var data = PetListUi(emptyList(), emptyList(), emptyList())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binder = PetsScreenBinder(view, this) { image, uri -> image.load(uri) }
        PcTypography.apply(view)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is ScreenState.Content -> {
                            data = state.data
                            binder.render(data.toPetsUiState())
                            PcTypography.apply(view)
                        }
                        ScreenState.Empty -> {
                            data = PetListUi(emptyList(), emptyList(), emptyList())
                            binder.render(data.toPetsUiState())
                            PcTypography.apply(view)
                        }
                        is ScreenState.Error -> UiSnackbar.make(view, state.message, Snackbar.LENGTH_LONG).show()
                        ScreenState.Loading -> Unit
                    }
                }
            }
        }
    }

    override fun onOpenPet(petId: Long, sharedView: View) {
        sharedView.transitionName = "pet_$petId"
        findNavController().navigate(R.id.action_pets_to_pet_detail, Bundle().apply { putLong("petId", petId) },
            null, FragmentNavigatorExtras(sharedView to "pet_$petId"))
    }

    override fun onPetMenu(petId: Long, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            inflate(R.menu.pet_card_actions)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_edit_pet -> { findNavController().navigate(R.id.action_pets_to_edit_pet,
                        Bundle().apply { putLong("petId", petId) }); true }
                    R.id.action_remove_pet -> { deletePet(petId); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun deletePet(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = viewModel.delete(id) ?: return@launch
            val scheduler = CareReminderScheduler(requireContext())
            snapshot.tasks.forEach { scheduler.cancel(it.id) }
            view?.let { root ->
                UiSnackbar.make(root, R.string.pet_deleted, Snackbar.LENGTH_LONG).setAction(R.string.undo) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.restore(snapshot)
                        snapshot.tasks.filterNot { it.isCompleted }.forEach(scheduler::schedule)
                    }
                }.show()
            }
        }
    }

    override fun onAddPet() { findNavController().navigate(R.id.action_pets_to_add_pet) }
    override fun onOpenComingUp(taskId: Long) {
        data.tasks.firstOrNull { it.id == taskId }?.let {
            TaskDetailSheet.newInstance(it).show(childFragmentManager, "task_detail")
        }
    }
    override fun onOpenMoney() { findNavController().navigate(R.id.expenseListFragment) }
    override fun onAddExpense() { findNavController().navigate(R.id.addExpenseFragment) }

    override fun onDestroyView() {
        view?.findViewById<RecyclerView>(R.id.pcPetGrid)?.adapter = null
        view?.findViewById<RecyclerView>(R.id.pcComingUp)?.adapter = null
        super.onDestroyView()
    }
}
