package com.example.petcare.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.petcare.R
import com.example.petcare.ImportActivity
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.design.InboxStore
import com.example.petcare.design.NextUpState
import com.example.petcare.design.NotificationPermissionRequester
import com.example.petcare.design.TodayActions
import com.example.petcare.design.TodayScreenBinder
import com.example.petcare.design.TodayUiState
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.integration.CarePlanEntry
import com.example.petcare.integration.CarePlanIcsCodec
import com.example.petcare.ui.ScreenState
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.UiSnackbar
import com.example.petcare.ui.PcTypography
import com.example.petcare.ui.toTodayUiState
import com.example.petcare.widget.CareWidgetProvider
import com.example.petcare.ui.integration.DelegationPreviewFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connects the approved Today design to existing account data and task actions. */
class HomeDashboardFragment : Fragment(R.layout.pc_fragment_today), TodayActions, SensorEventListener {
    private val viewModel: TodayViewModel by viewModels()
    private val scheduler by lazy { CareReminderScheduler(requireContext()) }
    private var binder: TodayScreenBinder? = null
    private var touchHelper: ItemTouchHelper? = null
    private var data = TodayData(emptyList(), emptyList(), emptyList(), emptyList())
    private var rendered: TodayUiState? = null
    private var lastLocation: Location? = null
    private var pendingTaskId = 0L
    private var pendingExport: String? = null
    private val shakeDetector = ShakeDetector()
    private val sensorManager by lazy {
        requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private var lastScrollAt = 0L
    private var resetDialogShowing = false
    private var resetDialog: AlertDialog? = null
    private val permissionRequester = NotificationPermissionRequester(this)
    private val importPlan = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        startActivity(Intent(requireContext(), ImportActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            type = requireContext().contentResolver.getType(uri) ?: "text/calendar"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
    private val exportPlan = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri == null || content == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        writer -> writer.write(content)
                    } ?: error("Unable to open export destination")
                }.isSuccess
            }
            view?.let { root ->
                UiSnackbar.make(
                    root,
                    if (saved) R.string.export_saved else R.string.export_failed,
                    Snackbar.LENGTH_LONG,
                ).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pendingTaskId = arguments?.getLong("taskId") ?: 0L
        val screen = TodayScreenBinder(view, viewLifecycleOwner, this) { image, uri -> image.load(uri) }
        binder = screen
        loadLastKnownLocation()
        val permissionPrefs = requireContext().getSharedPreferences("pc_notification_permission", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !permissionPrefs.getBoolean("requested_after_sign_in", false)
        ) {
            permissionPrefs.edit().putBoolean("requested_after_sign_in", true).apply()
            permissionRequester.requestIfNeeded(requireContext())
        }
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
        view.findViewById<androidx.core.widget.NestedScrollView>(R.id.pcTodayScroll)
            .setOnScrollChangeListener { _, _, _, _, _ ->
                lastScrollAt = SystemClock.elapsedRealtime()
                shakeDetector.clearWindow()
            }
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
                            ScreenState.Empty -> {
                                data = TodayData(emptyList(), emptyList(), emptyList(), emptyList())
                                render()
                            }
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

    override fun onResume() {
        super.onResume()
        shakeDetector.clearWindow()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        shakeDetector.clearWindow()
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || view == null || resetDialogShowing) return
        if (SystemClock.elapsedRealtime() - lastScrollAt < 850L) {
            shakeDetector.clearWindow()
            return
        }
        if (shakeDetector.addSample(event.values[0], event.values[1], event.values[2],
                event.timestamp / 1_000_000L)) {
            view?.let(GestureHaptics::confirm)
            confirmChecklistReset()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun confirmChecklistReset() {
        if (resetDialogShowing) return
        val root = view ?: return
        if (data.completed.none { it.dueDateEpochDay == LocalDayClock.todayEpochDay() }) {
            UiSnackbar.make(root, R.string.reset_today_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        resetDialogShowing = true
        resetDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reset_today_title)
            .setMessage(R.string.reset_today_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset_today_confirm) { _, _ -> resetChecklist() }
            .show().also { dialog ->
                dialog.setOnDismissListener {
                    resetDialog = null
                    resetDialogShowing = false
                    shakeDetector.clearWindow()
                }
            }
    }

    private fun resetChecklist() {
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshots = viewModel.resetCompleted(LocalDayClock.todayEpochDay())
            val root = view ?: return@launch
            if (snapshots.isEmpty()) {
                UiSnackbar.make(root, R.string.reset_today_empty, Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            val now = System.currentTimeMillis()
            val scheduled = snapshots.filter {
                LocalDayClock.dueMillis(it.dueDateEpochDay, it.reminderMinutesOfDay) > now
            }
            scheduled.forEach { scheduler.schedule(it.copy(isCompleted = false)) }
            UiSnackbar.make(root, R.string.reset_today_done, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        scheduled.forEach { scheduler.cancel(it.id) }
                        viewModel.restoreCompleted(snapshots)
                    }
                }.show()
        }
    }

    private fun render() {
        if (viewModel.selectedPetId != null && data.pets.none { it.id == viewModel.selectedPetId })
            viewModel.selectedPetId = null
        rendered = data.toTodayUiState(AuthPreferences(requireContext()).userName().orEmpty(),
            viewModel.selectedEpochDay, viewModel.selectedPetId, lastLocation).also {
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
    override fun onAddTask() {
        findNavController().navigate(
            R.id.action_home_to_add_care_task,
            Bundle().apply { putLong("preselectPetId", viewModel.selectedPetId ?: 0L) },
        )
    }
    override fun onAddPet() { findNavController().navigate(R.id.action_home_to_add_pet) }
    override fun onProfile() { findNavController().navigate(R.id.settingsFragment) }

    override fun onShareChecklist() {
        val tasks = rendered?.tasks.orEmpty()
        if (tasks.isEmpty()) {
            view?.let { UiSnackbar.make(it, R.string.nothing_to_share, Snackbar.LENGTH_LONG).show() }
            return
        }
        val message = buildString {
            appendLine(getString(R.string.today_checklist_heading))
            tasks.sortedBy { it.dueAtMillis }.forEach { task ->
                appendLine(getString(
                    R.string.today_checklist_line,
                    task.timeLabel,
                    task.petName,
                    task.title,
                    if (task.done) "✓" else "○",
                ))
            }
        }.trim()
        val petIds = data.pets.filter { pet -> tasks.any { it.petName == pet.name } }.map { it.id }.toLongArray()
        findNavController().navigate(
            R.id.action_home_to_delegation,
            Bundle().apply {
                putString(DelegationPreviewFragment.MESSAGE, message)
                putLongArray(DelegationPreviewFragment.PET_IDS, petIds)
            },
        )
    }

    override fun onNewRoutine() {
        findNavController().navigate(R.id.action_home_to_routine_generator)
    }

    override fun onImportPlan() {
        importPlan.launch(arrayOf("text/calendar", "application/ics", "text/plain"))
    }

    override fun onExportPlan() {
        val tasks = (data.upcoming + data.completed).distinctBy { it.id }.sortedWith(
            compareBy({ it.dueDateEpochDay }, { it.reminderMinutesOfDay }),
        )
        pendingExport = CarePlanIcsCodec.encode(tasks.map { task ->
            CarePlanEntry(
                id = task.id,
                title = "${task.petName}: ${task.title}",
                dateEpochDay = task.dueDateEpochDay,
                minutesOfDay = task.reminderMinutesOfDay,
                description = listOf(task.requiredSupplies, task.notes).filter { it.isNotBlank() }.joinToString("\n"),
            )
        })
        exportPlan.launch(getString(R.string.export_ics_name))
    }

    override fun onOpenNotifications() {
        findNavController().navigate(R.id.action_home_to_notifications)
    }

    override fun onOpenPlace(placeId: Long) {
        findNavController().navigate(
            R.id.providerListFragment,
            Bundle().apply { putLong("placeId", placeId) },
        )
    }

    private fun loadLastKnownLocation() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            LocationServices.getFusedLocationProviderClient(requireActivity()).lastLocation
                .addOnSuccessListener { location ->
                    if (view == null || location == null) return@addOnSuccessListener
                    lastLocation = location
                    render()
                }
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the Play Services call.
        }
    }

    private fun deleteTask(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val removed = viewModel.deleteTask(id) ?: return@launch
            scheduler.cancel(id)
            InboxStore.get(requireContext()).removeForTask(id)
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
        resetDialog?.dismiss()
        view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.pcTodayScroll)
            ?.setOnScrollChangeListener(null as androidx.core.widget.NestedScrollView.OnScrollChangeListener?)
        touchHelper?.attachToRecyclerView(null)
        touchHelper = null
        view?.findViewById<RecyclerView>(R.id.pcTimeline)?.adapter = null
        view?.findViewById<RecyclerView>(R.id.pcPetRings)?.adapter = null
        binder = null
        rendered = null
        super.onDestroyView()
    }
}
