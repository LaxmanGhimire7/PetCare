package com.example.petcare.ui.home

import com.example.petcare.ui.UiSnackbar
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.view.animation.LayoutAnimationController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.example.petcare.R
import com.example.petcare.ImportActivity
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.ui.ScreenState
import com.example.petcare.ui.care.SAVED_TASK_RESULT_KEY
import com.example.petcare.databinding.FragmentTodayBinding
import com.example.petcare.integration.IcsAppointmentParser
import com.example.petcare.integration.CarePlanEntry
import com.example.petcare.integration.CarePlanIcsCodec
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.ui.MotionPrefs
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.GestureCoachPrefs
import com.example.petcare.ui.RowMotion
import com.example.petcare.widget.CareWidgetProvider
import com.example.petcare.ui.integration.DelegationPreviewFragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Today's care, progress, pet filtering and the main care actions. */
class HomeDashboardFragment : Fragment(), SensorEventListener {
    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TodayViewModel by viewModels()
    private val scheduler by lazy { CareReminderScheduler(requireContext()) }
    private val filterAdapter = PetFilterAdapter(::selectPet)
    private lateinit var taskTouchHelper: ItemTouchHelper
    private val upcomingAdapter = TodayTaskAdapter(false, ::complete, ::edit, ::shareOne,
        ::openDetail, ::startTaskDrag)
    private val completedAdapter = TodayTaskAdapter(true, {}, ::edit, ::shareOne, ::openDetail)
    private var latestPets = emptyList<PetEntity>()
    private var latestUpcoming = emptyList<CareTaskSummary>()
    private var latestCompleted = emptyList<CareTaskSummary>()
    private var completedExpanded = false
    private var previousProgress: Pair<Int, Int>? = null
    private var pendingDeepLinkTaskId: Long = 0L
    private val shakeDetector = ShakeDetector()
    private val sensorManager by lazy {
        requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private var lastScrollAt = 0L
    private var resetDialogShowing = false
    private var reorderMode = false

    private val icsDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri -> if (uri != null) writeCarePlan(uri, calendarFile = true) }
    private val textDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> if (uri != null) writeCarePlan(uri, calendarFile = false) }

    private val appointmentPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            startActivity(Intent(requireContext(), ImportActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                // setData() and setType() clear each other; pass both together for SAF URIs.
                setDataAndType(uri,
                    requireContext().contentResolver.getType(uri) ?: "text/calendar")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentTodayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        pendingDeepLinkTaskId = arguments?.getLong("taskId") ?: 0L
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> R.string.today_greeting_morning
            in 12..17 -> R.string.today_greeting_afternoon
            else -> R.string.today_greeting_evening
        }
        val userName = AuthPreferences(requireContext()).userName().orEmpty().ifBlank {
            getString(R.string.default_pet_parent_name)
        }
        binding.greetingText.text = getString(greeting, userName)
        binding.todayDateText.text = DateFormat.getDateInstance(DateFormat.FULL).format(Date())
        binding.profileAvatarText.text = userName.trim().split(Regex("\\s+"))
            .filter(String::isNotBlank).take(2).joinToString("") { it.take(1).uppercase() }
        binding.profileAvatarText.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }
        binding.petFilterRecycler.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.petFilterRecycler.adapter = filterAdapter
        binding.taskRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.taskRecycler.adapter = upcomingAdapter
        binding.completedRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.completedRecycler.adapter = completedAdapter
        taskTouchHelper = ItemTouchHelper(TaskSwipeCallback({ viewHolder, direction ->
                val task = upcomingAdapter.taskAt(viewHolder.bindingAdapterPosition)
                if (direction == ItemTouchHelper.RIGHT) complete(task)
                else RowMotion.collapse(viewHolder.itemView) { delete(task) }
        }, upcomingAdapter::beginDrag, upcomingAdapter::moveItem, ::finishTaskDrag))
        taskTouchHelper.attachToRecyclerView(binding.taskRecycler)
        binding.addTaskFab.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_add_care_task)
        }
        binding.addTaskInline.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_add_care_task)
        }
        binding.todayEmptyAction.setOnClickListener { openEmptyAction() }
        binding.reorderModeButton.setOnClickListener { showTaskOptions() }
        binding.completedHeader.setOnClickListener {
            completedExpanded = !completedExpanded
            binding.completedRecycler.visibility = if (completedExpanded) View.VISIBLE else View.GONE
        }
        binding.generateRoutineButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_routine_generator)
        }
        binding.resetChecklistButton.setOnClickListener { confirmChecklistReset() }
        binding.shareChecklistButton.setOnClickListener { shareChecklist(latestUpcoming) }
        binding.exportPlanButton.setOnClickListener { chooseCarePlanExport() }
        binding.importAppointmentButton.setOnClickListener {
            appointmentPicker.launch(arrayOf("text/calendar", "application/ics", "text/plain"))
        }
        binding.todayScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // This switches FAB state immediately; it does not animate while scrolling.
            lastScrollAt = SystemClock.elapsedRealtime()
            shakeDetector.clearWindow()
            binding.addTaskFab.isExtended = scrollY < resources.getDimensionPixelSize(R.dimen.space_24)
        }
        binding.todayRefresh.setColorSchemeColors(MaterialColors.getColor(binding.root,
            androidx.appcompat.R.attr.colorPrimary))
        binding.todayRefresh.setOnChildScrollUpCallback { _, _ ->
            binding.todayScroll.canScrollVertically(-1)
        }
        binding.todayRefresh.setOnRefreshListener {
            GestureHaptics.confirm(binding.root)
            viewLifecycleOwner.lifecycleScope.launch {
                // Re-read Room and the local calendar day so midnight and overdue state refresh.
                viewModel.refresh()
                binding.todayRefresh.isRefreshing = false
                UiSnackbar.make(binding.root, R.string.today_refreshed, Snackbar.LENGTH_SHORT).show()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        ScreenState.Loading -> {
                            binding.loadingSkeleton.visibility = View.VISIBLE
                            binding.loadingSkeleton.start()
                        }
                        ScreenState.Empty -> {
                            latestPets = emptyList()
                            latestUpcoming = emptyList()
                            latestCompleted = emptyList()
                            render()
                        }
                        is ScreenState.Content -> {
                            latestPets = state.data.pets
                            latestUpcoming = state.data.upcoming
                            latestCompleted = state.data.completed
                            render()
                            CareWidgetProvider.refresh(requireContext().applicationContext)
                            if (pendingDeepLinkTaskId > 0L) {
                                val target = (latestUpcoming + latestCompleted)
                                    .firstOrNull { it.id == pendingDeepLinkTaskId }
                                pendingDeepLinkTaskId = 0L
                                if (target != null) openDetail(target)
                                else UiSnackbar.make(binding.root, R.string.task_unavailable,
                                    Snackbar.LENGTH_LONG).show()
                            }
                        }
                        is ScreenState.Error -> {
                            binding.loadingSkeleton.visibility = View.GONE
                            binding.loadingSkeleton.stop()
                            UiSnackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Long?>(SAVED_TASK_RESULT_KEY)
            ?.observe(viewLifecycleOwner) { savedId ->
                if (savedId != null) {
                    // A task saved for another pet must not remain hidden by an old filter.
                    viewModel.selectedPetId = null
                    findNavController().currentBackStackEntry?.savedStateHandle
                        ?.remove<Long>(SAVED_TASK_RESULT_KEY)
                    render()
                }
            }
    }

    private fun showGestureCoachIfNeeded() {
        val preferences = GestureCoachPrefs(requireContext())
        if (!preferences.shouldShow()) return
        val content = layoutInflater.inflate(R.layout.dialog_gesture_coach, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(content).create()
        content.findViewById<MaterialButton>(R.id.gesture_coach_done)
            .setOnClickListener { GestureHaptics.confirm(it); dialog.dismiss() }
        dialog.setOnDismissListener { preferences.markSeen() }
        dialog.show()
    }

    private fun selectPet(petId: Long?) {
        if (viewModel.selectedPetId == petId) return
        if (MotionPrefs.animationsEnabled(requireContext())) {
            TransitionManager.beginDelayedTransition(binding.taskRecycler, MaterialFadeThrough())
        }
        viewModel.selectedPetId = petId
        render()
    }

    private fun render() {
        val selectedId = viewModel.selectedPetId
        if (selectedId != null && latestPets.none { it.id == selectedId }) {
            viewModel.selectedPetId = null
        }
        filterAdapter.submit(latestPets, viewModel.selectedPetId)
        val selected = viewModel.selectedPetId
        val today = todayEpochDay()
        // The list includes future appointments; the ring still counts only today's care.
        val upcoming = latestUpcoming.filter { selected == null || it.petId == selected }
        val completedToday = latestCompleted.filter {
            it.dueDateEpochDay == today && (selected == null || it.petId == selected)
        }
        val doneCount = latestCompleted.count { it.dueDateEpochDay == today }
        val dueCount = latestUpcoming.count { it.dueDateEpochDay == today }
        val firstEntrance = viewModel.consumeFirstEntrance()
        val progress = doneCount to (doneCount + dueCount)
        val hasTasksToday = doneCount + dueCount > 0
        binding.progressRing.visibility = if (hasTasksToday) View.VISIBLE else View.GONE
        if (hasTasksToday) binding.progressRing.setProgress(
            doneCount, doneCount + dueCount,
            firstEntrance || (previousProgress != null && previousProgress != progress)
        )
        previousProgress = progress
        binding.progressCountText.text = if (hasTasksToday)
            getString(R.string.today_progress_count, doneCount, doneCount + dueCount)
        else getString(R.string.today_nothing_due)
        val nextToday = latestUpcoming.filter { it.dueDateEpochDay == today }
            .minByOrNull(CareTaskSummary::reminderMinutesOfDay)
        val nextFuture = latestUpcoming.filter { it.dueDateEpochDay > today }
            .minWithOrNull(compareBy(CareTaskSummary::dueDateEpochDay)
                .thenBy(CareTaskSummary::reminderMinutesOfDay))
        binding.nextTaskText.text = when {
            nextToday != null -> getString(R.string.today_next_task, nextToday.title,
                formatTime(nextToday.reminderMinutesOfDay))
            nextFuture != null -> getString(R.string.today_next_future_task, nextFuture.title,
                formatDate(nextFuture.dueDateEpochDay),
                formatTime(nextFuture.reminderMinutesOfDay))
            else -> getString(R.string.today_nothing_next)
        }
        binding.upcomingHeader.text = getString(R.string.today_upcoming_count, upcoming.size)
        binding.reorderModeButton.visibility = if (upcoming.isNotEmpty()) View.VISIBLE else View.GONE
        binding.completedHeader.text = getString(R.string.today_completed_count, completedToday.size)
        binding.completedHeader.visibility =
            if (completedToday.isEmpty()) View.GONE else View.VISIBLE
        binding.completedRecycler.visibility =
            if (completedExpanded && completedToday.isNotEmpty()) View.VISIBLE else View.GONE
        upcomingAdapter.todayEpochDay = today
        completedAdapter.todayEpochDay = today
        upcomingAdapter.submitList(upcoming)
        completedAdapter.submitList(completedToday)
        binding.loadingSkeleton.visibility = View.GONE
        binding.loadingSkeleton.stop()
        binding.todayEmptyCard.visibility = if (upcoming.isEmpty()) View.VISIBLE else View.GONE
        binding.todayEmptyText.setText(
            if (latestPets.isEmpty()) R.string.today_empty_pets else R.string.today_empty_tasks
        )
        binding.todayEmptyAction.setText(
            if (latestPets.isEmpty()) R.string.add_a_pet else R.string.add_task
        )
        // At large text sizes the floating button can cover a task's completion control.
        // Move the same action into the scrollable content once there are tasks.
        val inlineAdd = resources.configuration.fontScale >= 1.5f &&
            latestPets.isNotEmpty() && (upcoming.isNotEmpty() || completedToday.isNotEmpty())
        binding.addTaskInline.visibility = if (inlineAdd) View.VISIBLE else View.GONE
        binding.addTaskFab.visibility = if (latestPets.isEmpty() || inlineAdd) View.GONE
            else View.VISIBLE
        val hasPets = latestPets.isNotEmpty()
        binding.quickActionsContainer.visibility = if (hasPets) View.VISIBLE else View.GONE
        binding.resetChecklistButton.visibility = View.GONE
        binding.importSourcesText.visibility = View.GONE
        if (firstEntrance && MotionPrefs.animationsEnabled(requireContext())) playTaskEntrance()
    }

    /** Keeps reorder and destructive reset discoverable without placing controls under the FAB. */
    private fun showTaskOptions() {
        PopupMenu(requireContext(), binding.reorderModeButton).apply {
            inflate(R.menu.today_task_options)
            menu.findItem(R.id.action_reorder_tasks).setTitle(
                if (reorderMode) R.string.done_reordering else R.string.reorder_tasks
            )
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_reorder_tasks -> {
                        reorderMode = !reorderMode
                        upcomingAdapter.reorderMode = reorderMode
                        true
                    }
                    R.id.action_reset_checklist -> {
                        confirmChecklistReset()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    /** A 40 ms row interval makes the first data arrival legible without replaying on tab return. */
    private fun playTaskEntrance() {
        val animation = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f))
            addAnimation(TranslateAnimation(0f, 0f, 24f, 0f))
            duration = 300L
            interpolator = FastOutSlowInInterpolator()
        }
        binding.taskRecycler.layoutAnimation = LayoutAnimationController(animation, 40f / 300f)
        binding.taskRecycler.scheduleLayoutAnimation()
    }

    private fun openEmptyAction() {
        findNavController().navigate(
            if (latestPets.isEmpty()) R.id.action_home_to_add_pet else R.id.action_home_to_add_care_task
        )
    }

    private fun complete(task: CareTaskSummary) {
        viewLifecycleOwner.lifecycleScope.launch {
            val next = viewModel.completeTask(task.id)
            scheduler.cancel(task.id)
            next?.let(scheduler::schedule)
        }
    }

    private fun startTaskDrag(holder: RecyclerView.ViewHolder) {
        taskTouchHelper.startDrag(holder)
    }

    private fun finishTaskDrag() {
        val (ids, slots) = upcomingAdapter.finishDrag() ?: return
        if (ids == latestUpcoming.filter {
                viewModel.selectedPetId == null || it.petId == viewModel.selectedPetId }
                .map(CareTaskSummary::id)) return
        viewLifecycleOwner.lifecycleScope.launch { viewModel.saveOrder(ids, slots) }
    }

    override fun onResume() {
        super.onResume()
        binding.root.post { if (isResumed && _binding != null) showGestureCoachIfNeeded() }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || _binding == null) return
        // Scrolling can produce short acceleration peaks; discard that whole gesture window.
        if (SystemClock.elapsedRealtime() - lastScrollAt < 850L) {
            shakeDetector.clearWindow()
            return
        }
        if (shakeDetector.addSample(event.values[0], event.values[1], event.values[2],
                event.timestamp / 1_000_000L)) {
            GestureHaptics.confirm(binding.root)
            confirmChecklistReset()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun confirmChecklistReset() {
        if (resetDialogShowing) return
        if (latestCompleted.none { it.dueDateEpochDay == todayEpochDay() }) {
            UiSnackbar.make(binding.root, R.string.reset_today_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        resetDialogShowing = true
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reset_today_title)
            .setMessage(R.string.reset_today_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset_today_confirm) { _, _ -> resetChecklist() }
            .show().setOnDismissListener { resetDialogShowing = false }
    }

    private fun resetChecklist() {
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshots = viewModel.resetCompleted(todayEpochDay())
            if (snapshots.isEmpty()) return@launch
            val minutesNow = Calendar.getInstance().let {
                it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
            }
            snapshots.filter { it.reminderMinutesOfDay > minutesNow }.forEach(scheduler::schedule)
            UiSnackbar.make(binding.root, R.string.reset_today_done, Snackbar.LENGTH_LONG)
                .setDuration(6000)
                .setAction(R.string.undo) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        snapshots.forEach { scheduler.cancel(it.id) }
                    viewModel.restoreCompleted(snapshots)
                    }
                }.show()
        }
    }

    private fun delete(task: CareTaskSummary) {
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = viewModel.deleteTask(task.id) ?: return@launch
            scheduler.cancel(task.id)
            UiSnackbar.make(binding.root, R.string.task_deleted, Snackbar.LENGTH_LONG)
                .setDuration(6000)
                .setAction(R.string.undo) {
                    upcomingAdapter.restoringTaskId = snapshot.id
                    viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.restoreTask(snapshot)
                        if (!snapshot.isCompleted) scheduler.schedule(snapshot)
                    }
                }.show()
        }
    }

    private fun edit(task: CareTaskSummary) {
        findNavController().navigate(R.id.action_home_to_edit_care_task, Bundle().apply {
            putLong("careTaskId", task.id)
        })
    }

    private fun shareOne(task: CareTaskSummary) = shareChecklist(listOf(task))

    private fun openDetail(task: CareTaskSummary) {
        TaskDetailSheet.newInstance(task).show(childFragmentManager, "task_detail")
    }

    private fun shareChecklist(items: List<CareTaskSummary>) {
        if (items.isEmpty()) {
            UiSnackbar.make(binding.root, R.string.nothing_to_share, Snackbar.LENGTH_SHORT).show()
            return
        }
        val body = carePlanBody(items)
        findNavController().navigate(R.id.action_home_to_delegation, Bundle().apply {
            putString(DelegationPreviewFragment.MESSAGE, body)
            putLongArray(DelegationPreviewFragment.PET_IDS, items.map { it.petId }.distinct().toLongArray())
        })
    }

    private fun carePlanBody(items: List<CareTaskSummary>): String = buildString {
            appendLine(getString(R.string.today_checklist_heading))
            items.forEach { task ->
                appendLine(getString(
                    R.string.today_checklist_line,
                    task.petName,
                    task.title,
                    formatDate(task.dueDateEpochDay) + " " + formatTime(task.reminderMinutesOfDay),
                    task.frequency
                ))
                if (task.requiredSupplies.isNotBlank()) {
                    appendLine(getString(R.string.today_checklist_supplies, task.requiredSupplies))
                }
                if (task.notes.isNotBlank()) {
                    appendLine(getString(R.string.care_plan_instructions, task.notes))
                }
            }
        }

    private fun chooseCarePlanExport() {
        if (latestUpcoming.isEmpty()) {
            UiSnackbar.make(binding.root, R.string.nothing_to_share, Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.export_care_plan)
            .setItems(arrayOf(getString(R.string.export_calendar_file),
                getString(R.string.export_text_file))) { _, choice ->
                if (choice == 0) icsDocument.launch(getString(R.string.export_ics_name))
                else textDocument.launch(getString(R.string.export_text_name))
            }.show()
    }

    private fun writeCarePlan(uri: Uri, calendarFile: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = viewModel.upcoming()
            val content = if (calendarFile) CarePlanIcsCodec.encode(items.map { task ->
                val description = listOf(task.petName,
                    task.requiredSupplies.takeIf(String::isNotBlank)?.let {
                        getString(R.string.today_checklist_supplies, it)
                    }, task.notes.takeIf(String::isNotBlank)).filterNotNull().joinToString("\n")
                CarePlanEntry(task.id, task.title, task.dueDateEpochDay,
                    task.reminderMinutesOfDay, description,
                    if (task.latitude != null && task.longitude != null)
                        "${task.latitude},${task.longitude}" else "")
            }) else carePlanBody(items)
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("No output stream")
                }.isSuccess
            }
            UiSnackbar.make(binding.root,
                if (saved) R.string.export_saved else R.string.export_failed,
                Snackbar.LENGTH_LONG).show()
        }
    }

    private fun formatTime(minutes: Int): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }.time
    )
    private fun formatDate(day: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(day * 86_400_000L))

    /** Converts the local calendar date to the epoch-day convention stored by Room. */
    private fun todayEpochDay(): Long {
        val local = Calendar.getInstance()
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis / 86_400_000L
    }

    override fun onDestroyView() {
        binding.loadingSkeleton.stop()
        binding.petFilterRecycler.adapter = null
        binding.taskRecycler.adapter = null
        binding.completedRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
