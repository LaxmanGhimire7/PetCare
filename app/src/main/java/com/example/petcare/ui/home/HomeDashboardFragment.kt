package com.example.petcare.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.design.NextUpState
import com.example.petcare.design.TodayActions
import com.example.petcare.design.TodayScreenBinder
import com.example.petcare.design.TodayUiState
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.ui.ScreenState
import com.example.petcare.ui.UiSnackbar
import com.example.petcare.ui.PcTypography
import com.example.petcare.ui.toTodayUiState
import com.example.petcare.widget.CareWidgetProvider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Connects the approved Today design to existing account data and task actions. */
class HomeDashboardFragment : Fragment(R.layout.pc_fragment_today), TodayActions {
    private val viewModel: TodayViewModel by viewModels()
    private val scheduler by lazy { CareReminderScheduler(requireContext()) }
    private var binder: TodayScreenBinder? = null
    private var touchHelper: ItemTouchHelper? = null
    private var data = TodayData(emptyList(), emptyList(), emptyList())
    private var rendered: TodayUiState? = null
    private var pendingTaskId = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pendingTaskId = arguments?.getLong("taskId") ?: 0L
        val screen = TodayScreenBinder(view, viewLifecycleOwner, this) { image, uri -> image.load(uri) }
        binder = screen
        PcTypography.apply(view)
        touchHelper = ItemTouchHelper(TaskSwipeCallback(
            onAction = { holder, direction ->
                val position = holder.bindingAdapterPosition
                screen.timelineAdapter.taskAt(position)?.let { task ->
                    screen.timelineAdapter.notifyItemChanged(position)
                    if (direction == ItemTouchHelper.RIGHT) onToggleTask(task.id, !task.done)
                    else deleteTask(task.id)
                }
            },
            onDragStart = {}, onDragMove = { _, _ -> false }, onDragFinish = {},
            completeColor = { holder ->
                screen.timelineAdapter.taskAt(holder.bindingAdapterPosition)?.petColor?.main(requireContext()) ?: 0
            },
            canSwipe = { screen.timelineAdapter.taskAt(it.bindingAdapterPosition) != null },
        )).also { it.attachToRecyclerView(view.findViewById(R.id.pcTimeline)) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        when (state) {
                            is ScreenState.Content -> {
                                data = state.data
                                render()
                                CareWidgetProvider.refresh(requireContext().applicationContext)
                                if (pendingTaskId != 0L) {
                                    onOpenTask(pendingTaskId)
                                    pendingTaskId = 0L
                                }
                            }
                            ScreenState.Empty -> { data = TodayData(emptyList(), emptyList(), emptyList()); render() }
                            is ScreenState.Error -> UiSnackbar.make(view, state.message, Snackbar.LENGTH_LONG)
                                .setAction(R.string.pc_retry) { viewModel.refresh() }.show()
                            ScreenState.Loading -> Unit
                        }
                    }
                }
                launch {
                    var day = LocalDayClock.todayEpochDay()
                    while (isActive) {
                        delay(60_000)
                        val newDay = LocalDayClock.todayEpochDay()
                        if (viewModel.selectedEpochDay == day) viewModel.selectedEpochDay = newDay
                        day = newDay
                        render()
                    }
                }
            }
        }
    }

    private fun render() {
        if (viewModel.selectedPetId != null && data.pets.none { it.id == viewModel.selectedPetId })
            viewModel.selectedPetId = null
        rendered = data.toTodayUiState(AuthPreferences(requireContext()).userName().orEmpty(),
            viewModel.selectedEpochDay, viewModel.selectedPetId).also {
                binder?.render(it)
                view?.let(PcTypography::apply)
            }
    }

    override fun onToggleTask(taskId: Long, done: Boolean) {
        val task = (data.upcoming + data.completed).firstOrNull { it.id == taskId } ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (done) {
                val next = viewModel.completeTask(taskId)
                scheduler.cancel(taskId)
                next?.let(scheduler::schedule)
                view?.let { root ->
                    UiSnackbar.make(root, getString(R.string.task_done_message, task.title), Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) {
                            viewLifecycleOwner.lifecycleScope.launch { viewModel.reopenTask(taskId)?.let(scheduler::schedule) }
                        }.show()
                }
            } else viewModel.reopenTask(taskId)?.let(scheduler::schedule)
        }
    }

    override fun onOpenTask(taskId: Long) {
        (data.upcoming + data.completed).firstOrNull { it.id == taskId }?.let {
            TaskDetailSheet.newInstance(it).show(childFragmentManager, "task_detail")
        }
    }

    override fun onSelectPet(key: String) { viewModel.selectedPetId = key.toLongOrNull(); render() }
    override fun onSelectDay(index: Int) {
        viewModel.selectedEpochDay = LocalDayClock.weekStart(viewModel.selectedEpochDay) + index
        render()
    }

    private fun nextTaskId(): Long? {
        val next = rendered?.nextUp as? NextUpState.Upcoming ?: return null
        return rendered?.tasks?.firstOrNull {
            !it.done && it.dueAtMillis == next.dueAtMillis && it.title == next.title && it.petName == next.petName
        }?.id
    }

    override fun onNextUpDone() { nextTaskId()?.let { onToggleTask(it, true) } }
    override fun onNextUpSnooze() {
        val id = nextTaskId() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.snoozeTask(id, 30)?.let { scheduler.cancel(id); scheduler.schedule(it) }
        }
    }
    override fun onPlanTask() = onAddTask()
    override fun onAddTask() { findNavController().navigate(R.id.action_home_to_add_care_task) }
    override fun onAddPet() { findNavController().navigate(R.id.action_home_to_add_pet) }
    override fun onProfile() { findNavController().navigate(R.id.settingsFragment) }

    private fun deleteTask(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val removed = viewModel.deleteTask(id) ?: return@launch
            scheduler.cancel(id)
            view?.let { root ->
                UiSnackbar.make(root, R.string.task_deleted, Snackbar.LENGTH_LONG).setAction(R.string.undo) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.restoreTask(removed)
                        if (!removed.isCompleted) scheduler.schedule(removed)
                    }
                }.show()
            }
        }
    }

    override fun onDestroyView() {
        touchHelper?.attachToRecyclerView(null)
        touchHelper = null
        view?.findViewById<RecyclerView>(R.id.pcTimeline)?.adapter = null
        view?.findViewById<RecyclerView>(R.id.pcPetRings)?.adapter = null
        binder = null
        rendered = null
        super.onDestroyView()
    }
}
