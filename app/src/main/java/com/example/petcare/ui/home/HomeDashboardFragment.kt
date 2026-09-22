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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private val taskAdapter = TodayTaskAdapter(::complete, ::reopen, ::edit, ::shareOne,
        ::openDetail, ::startTaskDrag)
    private val weekAdapter = WeekStripAdapter(::selectDay)
    private var latestPets = emptyList<PetEntity>()
    private var latestUpcoming = emptyList<CareTaskSummary>()
    private var latestCompleted = emptyList<CareTaskSummary>()
    private var nextCardTask: CareTaskSummary? = null
    private var hideCompleted = false
    private var lastClockDay = 0L
    private var lastNowMinute = -1
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
        binding.weekRecycler.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.weekRecycler.adapter = weekAdapter
        binding.taskRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.taskRecycler.adapter = taskAdapter
        hideCompleted = requireContext().getSharedPreferences(TODAY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(HIDE_COMPLETED, false)
        taskTouchHelper = ItemTouchHelper(TaskSwipeCallback({ viewHolder, direction ->
                val task = taskAdapter.taskAt(viewHolder.bindingAdapterPosition)
                if (direction == ItemTouchHelper.RIGHT) {
                    if (task.isCompleted) reopen(task) else complete(task)
                }
                else RowMotion.collapse(viewHolder.itemView) { delete(task) }
        }, taskAdapter::beginDrag, taskAdapter::moveItem, ::finishTaskDrag, { holder ->
            val task = taskAdapter.taskAt(holder.bindingAdapterPosition)
            com.example.petcare.data.local.pet.PetColor.fromIndex(task.petColorIndex)
                .color(requireContext())
        }))
        taskTouchHelper.attachToRecyclerView(binding.taskRecycler)
        binding.addTaskFab.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_add_care_task)
        }
        binding.addTaskInline.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_add_care_task)
        }
        binding.todayEmptyAction.setOnClickListener { openEmptyAction() }
        binding.reorderModeButton.setOnClickListener { showTaskOptions() }
        binding.generateRoutineButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_routine_generator)
        }
        binding.shareChecklistButton.setOnClickListener { shareChecklist(latestUpcoming) }
        binding.exportPlanButton.setOnClickListener { chooseCarePlanExport() }
        binding.importAppointmentButton.setOnClickListener {
            appointmentPicker.launch(arrayOf("text/calendar", "application/ics", "text/plain"))
        }
        binding.todayScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            lastScrollAt = SystemClock.elapsedRealtime()
            shakeDetector.clearWindow()
        }
        binding.nextMarkDoneButton.setOnClickListener { nextCardTask?.let(::complete) }
        binding.nextSnoozeButton.setOnClickListener { nextCardTask?.let(::snooze) }
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
                launch {
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
                launch {
                    // Recalculate from the wall clock; background time and delayed ticks never drift.
                    while (isActive) {
                        updateClockPresentation()
                        delay(1_000L)
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

    private fun selectDay(day: Long) {
        if (viewModel.selectedEpochDay == day) return
        viewModel.selectedEpochDay = day
        render()
    }

    private fun render() {
        if (viewModel.selectedPetId != null && latestPets.none { it.id == viewModel.selectedPetId }) {
            viewModel.selectedPetId = null
        }
        val today = todayEpochDay()
        if (viewModel.selectedEpochDay == 0L) viewModel.selectedEpochDay = today
        val selectedDay = viewModel.selectedEpochDay
        val selectedPet = viewModel.selectedPetId
        val allTasks = (latestUpcoming + latestCompleted).distinctBy(CareTaskSummary::id)
        val onSelectedDay = allTasks.filter { it.dueDateEpochDay == selectedDay }
        val overdue = if (selectedDay == today) allTasks.filter {
            !it.isCompleted && it.dueDateEpochDay < today
        } else emptyList()
        val chronological = TodayTimeline.select(latestUpcoming, latestCompleted, selectedDay,
            today, selectedPet, hideCompleted = false)
        val hiddenCount = chronological.count(CareTaskSummary::isCompleted)
        val visible = if (hideCompleted) chronological.filterNot(CareTaskSummary::isCompleted) else chronological
        val progressTasks = onSelectedDay.filter { selectedPet == null || it.petId == selectedPet }
        val doneCount = progressTasks.count(CareTaskSummary::isCompleted)

        val petProgress = latestPets.associate { pet ->
            val tasks = onSelectedDay.filter { it.petId == pet.id }
            pet.id to (tasks.count(CareTaskSummary::isCompleted) to tasks.size)
        }
        filterAdapter.submit(latestPets, selectedPet, petProgress,
            doneCount to progressTasks.size)
        weekAdapter.submit(selectedDay, allTasks.map(CareTaskSummary::dueDateEpochDay).toSet())
        binding.progressRing.setSegments(progressTasks.map { task ->
            com.example.petcare.data.local.pet.PetColor.fromIndex(task.petColorIndex)
                .color(requireContext()) to task.isCompleted
        })
        binding.progressRing.visibility = if (progressTasks.isEmpty()) View.GONE else View.VISIBLE
        binding.progressCountText.text = getString(R.string.today_done_progress, doneCount, progressTasks.size)

        taskAdapter.todayEpochDay = today
        val nowMinutes = currentMinutes()
        val nextAfterNow = if (selectedDay == today) visible.firstOrNull {
            it.dueDateEpochDay == today && it.reminderMinutesOfDay > nowMinutes
        } else null
        taskAdapter.nowBeforeTaskId = nextAfterNow?.id
        taskAdapter.submitList(visible)
        binding.nowAfterTimeline.visibility = if (selectedDay == today && visible.isNotEmpty() && nextAfterNow == null)
            View.VISIBLE else View.GONE
        binding.nowAfterTime.text = formatTime(nowMinutes)

        binding.upcomingHeader.text = if (hideCompleted && hiddenCount > 0)
            getString(R.string.completed_hidden, hiddenCount) else getString(R.string.timeline)
        binding.upcomingHeader.setOnClickListener {
            if (hideCompleted && hiddenCount > 0) toggleCompletedVisibility()
        }

        renderNextCard(progressTasks, overdue, selectedDay, today)
        binding.loadingSkeleton.visibility = View.GONE
        binding.loadingSkeleton.stop()
        val hasPets = latestPets.isNotEmpty()
        binding.todayContent.visibility = if (hasPets) View.VISIBLE else View.GONE
        binding.todayEmptyCard.visibility = if (hasPets) View.GONE else View.VISIBLE
        binding.todayEmptyText.setText(R.string.today_empty_pets_title)
        binding.todayEmptyAction.setText(R.string.add_a_pet)
        binding.reorderModeButton.visibility = if (hasPets) View.VISIBLE else View.GONE

        val inlineAdd = resources.configuration.fontScale >= 1.5f && hasPets
        binding.addTaskInline.visibility = if (inlineAdd) View.VISIBLE else View.GONE
        binding.addTaskFab.visibility = if (!hasPets || inlineAdd) View.GONE else View.VISIBLE
        if (viewModel.consumeFirstEntrance() && MotionPrefs.animationsEnabled(requireContext())) {
            binding.progressRing.alpha = 0f
            binding.progressRing.animate().alpha(1f).setDuration(600L).start()
        }
    }

    /** Resolves every hero state from the selected date and current wall clock. */
    private fun renderNextCard(
        selectedDayTasks: List<CareTaskSummary>,
        allOverdue: List<CareTaskSummary>,
        selectedDay: Long,
        today: Long
    ) {
        val selectedPet = viewModel.selectedPetId
        val overdueCount = allOverdue.count { selectedPet == null || it.petId == selectedPet }
        binding.nextOverdueText.visibility = if (overdueCount > 0) View.VISIBLE else View.GONE
        binding.nextOverdueText.text = getString(R.string.overdue_count, overdueCount)
        val open = selectedDayTasks.filterNot(CareTaskSummary::isCompleted)
            .sortedBy(CareTaskSummary::reminderMinutesOfDay)
        val now = currentMinutes()
        val next = if (selectedDay == today)
            open.firstOrNull { it.reminderMinutesOfDay >= now } ?: open.firstOrNull()
        else open.firstOrNull()
        nextCardTask = next

        when {
            next != null -> {
                binding.nextTaskText.text = next.title
                val petColor = com.example.petcare.data.local.pet.PetColor.fromIndex(next.petColorIndex)
                binding.nextPetText.visibility = View.VISIBLE
                binding.nextPetText.text = next.petName
                binding.nextPetText.setTextColor(petColor.onContainer(requireContext()))
                binding.nextPetText.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = resources.getDimension(R.dimen.radius_pill)
                    setColor(petColor.container(requireContext()))
                }
                binding.nextActions.visibility = View.VISIBLE
                binding.nextMarkDoneButton.setText(R.string.mark_done)
                binding.nextMarkDoneButton.setOnClickListener { nextCardTask?.let(::complete) }
                binding.nextSnoozeButton.visibility = if (selectedDay == today) View.VISIBLE else View.GONE
                if (selectedDay == today) updateClockPresentation()
                else {
                    binding.countdownText.visibility = View.GONE
                    binding.countdownUntilText.visibility = View.VISIBLE
                    binding.countdownUntilText.text = getString(R.string.first_task_on_date,
                        formatTime(next.reminderMinutesOfDay))
                }
            }
            selectedDayTasks.isNotEmpty() && selectedDayTasks.all(CareTaskSummary::isCompleted) -> {
                showStaticNextState(R.string.all_done_today)
            }
            else -> {
                showStaticNextState(R.string.nothing_planned_today)
                binding.nextActions.visibility = View.VISIBLE
                binding.nextMarkDoneButton.setText(R.string.plan_a_task)
                binding.nextMarkDoneButton.setOnClickListener {
                    findNavController().navigate(R.id.action_home_to_add_care_task)
                }
                binding.nextSnoozeButton.visibility = View.GONE
            }
        }
    }

    private fun showStaticNextState(message: Int) {
        nextCardTask = null
        binding.nextTaskText.setText(message)
        binding.nextPetText.visibility = View.GONE
        binding.countdownText.visibility = View.GONE
        binding.countdownUntilText.visibility = View.GONE
        binding.nextActions.visibility = View.GONE
    }

    /** Recomputes the countdown and Now position from the clock on every active tick. */
    private fun updateClockPresentation() {
        if (_binding == null) return
        val today = todayEpochDay()
        if (lastClockDay == 0L) lastClockDay = today
        if (today != lastClockDay) {
            if (viewModel.selectedEpochDay == lastClockDay) viewModel.selectedEpochDay = today
            lastClockDay = today
            viewModel.refresh()
            render()
            return
        }
        if (viewModel.selectedEpochDay == today) {
            val minutes = currentMinutes()
            binding.nowAfterTime.text = formatTime(minutes)
            if (minutes != lastNowMinute) {
                lastNowMinute = minutes
                val current = taskAdapter.currentList
                taskAdapter.nowBeforeTaskId = current.firstOrNull {
                    it.dueDateEpochDay == today && it.reminderMinutesOfDay > minutes
                }?.id
                binding.nowAfterTimeline.visibility = if (current.isNotEmpty() && taskAdapter.nowBeforeTaskId == null)
                    View.VISIBLE else View.GONE
                taskAdapter.notifyDataSetChanged()
            }
        }
        val task = nextCardTask ?: return
        if (viewModel.selectedEpochDay != today) return
        val remaining = taskDueMillis(task) - System.currentTimeMillis()
        binding.countdownText.visibility = View.VISIBLE
        binding.countdownUntilText.visibility = View.VISIBLE
        if (remaining <= 0L) {
            binding.countdownText.setText(R.string.countdown_due_now)
            binding.countdownText.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_text))
            binding.countdownText.contentDescription = getString(R.string.countdown_due_now)
        } else {
            val totalSeconds = remaining / 1_000L
            val formatted = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d",
                totalSeconds / 3_600L, totalSeconds / 60L % 60L, totalSeconds % 60L)
            val styled = SpannableString(formatted)
            formatted.indices.filter { formatted[it] == ':' }.forEach { index ->
                styled.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.primary)),
                    index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            styled.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_secondary)),
                formatted.length - 2, formatted.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.countdownText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            binding.countdownText.text = styled
            binding.countdownText.contentDescription = getString(R.string.countdown_accessibility,
                task.title, humanDuration(remaining))
        }
        binding.countdownUntilText.text = getString(R.string.countdown_until,
            formatTime(task.reminderMinutesOfDay))
    }

    private fun humanDuration(milliseconds: Long): String {
        val minutes = (milliseconds / 60_000L).coerceAtLeast(1L)
        return resources.getQuantityString(R.plurals.minutes_count, minutes.toInt(), minutes)
    }

    private fun taskDueMillis(task: CareTaskSummary): Long =
        LocalDayClock.dueMillis(task.dueDateEpochDay, task.reminderMinutesOfDay)

    private fun currentMinutes(): Int = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }

    private fun toggleCompletedVisibility() {
        hideCompleted = !hideCompleted
        requireContext().getSharedPreferences(TODAY_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(HIDE_COMPLETED, hideCompleted).apply()
        render()
    }

    /** Keeps reorder and destructive reset discoverable without placing controls under the FAB. */
    private fun showTaskOptions() {
        PopupMenu(requireContext(), binding.reorderModeButton).apply {
            inflate(R.menu.today_task_options)
            menu.findItem(R.id.action_reorder_tasks).setTitle(
                if (reorderMode) R.string.done_reordering else R.string.reorder_tasks
            )
            menu.findItem(R.id.action_hide_completed).setTitle(
                if (hideCompleted) R.string.show_completed else R.string.hide_completed
            )
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_reorder_tasks -> {
                        reorderMode = !reorderMode
                        taskAdapter.reorderMode = reorderMode
                        true
                    }
                    R.id.action_hide_completed -> {
                        toggleCompletedVisibility()
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
            UiSnackbar.make(binding.root, getString(R.string.task_done_message, task.title),
                Snackbar.LENGTH_LONG).setAction(R.string.undo) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.reopenTask(task.id)?.let(scheduler::schedule)
                }
            }.show()
        }
    }

    private fun reopen(task: CareTaskSummary) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.reopenTask(task.id)?.let(scheduler::schedule)
            UiSnackbar.make(binding.root, getString(R.string.task_reopened_message, task.title),
                Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun snooze(task: CareTaskSummary) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.snoozeTask(task.id, 30)?.let { moved ->
                scheduler.cancel(task.id)
                scheduler.schedule(moved)
                UiSnackbar.make(binding.root, R.string.snoozed_thirty_minutes,
                    Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun startTaskDrag(holder: RecyclerView.ViewHolder) {
        taskTouchHelper.startDrag(holder)
    }

    private fun finishTaskDrag() {
        val (ids, slots) = taskAdapter.finishDrag() ?: return
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
                    taskAdapter.restoringTaskId = snapshot.id
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
        binding.weekRecycler.adapter = null
        binding.taskRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val TODAY_PREFS = "today_display"
        const val HIDE_COMPLETED = "hide_completed"
    }
}
