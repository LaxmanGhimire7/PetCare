package com.example.petcare.design

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the Today screen shows. Your ViewModel maps its data into this. */
data class TodayUiState(
    val dateLabel: String,
    val greeting: String,
    val userInitial: String,
    val hasPets: Boolean,
    /** True when the selected week-strip day is today: turns on the countdown and Now marker. */
    val isToday: Boolean,
    val nextUp: NextUpState,
    val petRings: List<PetRingItem>,
    val week: List<WeekDay>,
    /** Tasks for the selected day (plus overdue ones when isToday). Any order: the binder sorts by time. */
    val tasks: List<TimelineItem.Task>,
    /** Streak, week score, next health date and vet. Build the first two with GlanceMath.summary. */
    val glance: GlanceState = GlanceState.EMPTY,
)

/** Every interaction on Today. Implement by delegating to your existing ViewModel methods. */
interface TodayActions {
    fun onToggleTask(taskId: Long, done: Boolean)
    fun onOpenTask(taskId: Long)
    fun onSelectPet(key: String)
    fun onSelectDay(index: Int)
    fun onNextUpDone()
    fun onNextUpSnooze()
    fun onPlanTask()
    fun onAddTask()
    fun onAddPet()
    fun onProfile()

    /** Opens the existing SMS delegation flow with today's checklist. */
    fun onShareChecklist()
    fun onNewRoutine()
    fun onImportPlan()
    fun onExportPlan()
    fun onOpenNotifications()

    /** Tapping the vet card: open that saved place. */
    fun onOpenPlace(placeId: Long) = Unit
}

/**
 * Binds pc_fragment_today.xml. Create once in onViewCreated with viewLifecycleOwner,
 * then call [render] whenever your state changes. It owns the countdown tick and
 * the minute-by-minute Now marker; you never need to refresh those yourself.
 */
class TodayScreenBinder(
    root: View,
    private val lifecycleOwner: LifecycleOwner,
    private val actions: TodayActions,
    loadPhoto: (ImageView, String) -> Unit,
) {
    private val context = root.context
    private val date: TextView = root.findViewById(R.id.pcTodayDate)
    private val greeting: TextView = root.findViewById(R.id.pcTodayGreeting)
    private val avatar: TextView = root.findViewById(R.id.pcTodayAvatar)
    private val bell: View = root.findViewById(R.id.pcTodayBell)
    private val badge: TextView = root.findViewById(R.id.pcTodayBadge)
    private val quickActions: View = root.findViewById(R.id.pcQuickActions)
    private val glanceRoot: View = root.findViewById(R.id.pcGlance)
    private val glance = GlanceBinder(
        glanceRoot,
        onOpenTask = { actions.onOpenTask(it) },
        onShareAgain = { actions.onShareChecklist() },
        onOpenPlace = { actions.onOpenPlace(it) },
    )
    private val empty: View = root.findViewById(R.id.pcTodayEmpty)
    private val nextUpRoot: View = root.findViewById(R.id.pcNextUp)
    private val petRings: RecyclerView = root.findViewById(R.id.pcPetRings)
    private val weekStrip: LinearLayout = root.findViewById(R.id.pcWeekStrip)
    private val timeline: RecyclerView = root.findViewById(R.id.pcTimeline)
    private val timelineHeader: View = root.findViewById(R.id.pcTimelineHeader)
    private val timelineTitle: TextView = root.findViewById(R.id.pcTimelineTitle)
    private val timelineCount: TextView = root.findViewById(R.id.pcTimelineCount)
    private val timelineEmpty: View = root.findViewById(R.id.pcTimelineEmpty)
    private val fab: View = root.findViewById(R.id.pcTodayFab)

    private val ringAdapter = PetRingAdapter(loadPhoto) { actions.onSelectPet(it) }

    /** Exposed so you can attach your existing ItemTouchHelper swipe gestures to it. */
    val timelineAdapter = TimelineAdapter(
        onToggle = { task, done -> actions.onToggleTask(task.id, done) },
        onOpen = { task -> actions.onOpenTask(task.id) },
    )

    private val nextUp = NextUpBinder(
        nextUpRoot,
        onDone = actions::onNextUpDone,
        onSnooze = actions::onNextUpSnooze,
        onPlan = actions::onPlanTask,
        onRoutine = actions::onNewRoutine,
    )

    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var state: TodayUiState? = null
    private var firstContentRender = true
    private var lastMinute = -1L

    init {
        root.applySystemBarTopPadding()

        petRings.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        petRings.adapter = ringAdapter
        timeline.layoutManager = LinearLayoutManager(context)
        timeline.adapter = timelineAdapter

        avatar.setOnClickListener { actions.onProfile() }
        bell.setOnClickListener { actions.onOpenNotifications() }
        root.findViewById<View>(R.id.pcActionShare).setOnClickListener { actions.onShareChecklist() }
        root.findViewById<View>(R.id.pcActionRoutine).setOnClickListener { actions.onNewRoutine() }
        root.findViewById<View>(R.id.pcActionImport).setOnClickListener { actions.onImportPlan() }
        root.findViewById<View>(R.id.pcActionExport).setOnClickListener { actions.onExportPlan() }
        fab.setOnClickListener { actions.onAddTask() }
        root.findViewById<View>(R.id.pcTodayEmptyAddPet).setOnClickListener { actions.onAddPet() }

        startTicker()
        observeUnread()
    }

    fun render(newState: TodayUiState) {
        state = newState
        date.text = newState.dateLabel
        greeting.text = newState.greeting
        avatar.text = newState.userInitial

        val content = if (newState.hasPets) View.VISIBLE else View.GONE
        empty.visibility = if (newState.hasPets) View.GONE else View.VISIBLE
        nextUpRoot.visibility = content
        petRings.visibility = content
        quickActions.visibility = content
        glanceRoot.visibility = content
        timelineHeader.visibility = content
        weekStrip.visibility = content
        timeline.visibility = content
        fab.visibility = content
        if (!newState.hasPets) {
            timelineEmpty.visibility = View.GONE
            return
        }

        // The one orchestrated moment: rings and progress sweep up once per screen creation.
        val sweep = firstContentRender && MotionPrefs.animationsEnabled(context)
        firstContentRender = false

        nextUp.render(newState.nextUp, animateBar = sweep)
        nextUp.tick(System.currentTimeMillis())
        ringAdapter.submit(newState.petRings, animateFromEmpty = sweep)
        WeekStripBinder.bind(weekStrip, newState.week) { actions.onSelectDay(it) }
        glance.render(newState.glance)
        bindPlanHeader(newState)

        timelineEmpty.visibility = if (newState.tasks.isEmpty()) View.VISIBLE else View.GONE
        timeline.visibility = if (newState.tasks.isEmpty()) View.GONE else View.VISIBLE
        submitTimeline(System.currentTimeMillis())
    }

    private fun submitTimeline(nowMillis: Long) {
        val current = state ?: return
        if (!current.hasPets || current.tasks.isEmpty()) return
        val items = if (current.isToday) {
            TimelineBuilder.build(current.tasks, nowMillis, clock.format(Date(nowMillis)))
        } else {
            TimelineBuilder.build(current.tasks, null, null)
        }
        timelineAdapter.submitList(items) { timelineAdapter.refreshConnectors() }
    }

    /** "Today's plan  3 left", or "Plan for Wed 30" when another day is selected. */
    private fun bindPlanHeader(state: TodayUiState) {
        val selected = state.week.firstOrNull { it.selected }
        timelineTitle.text = if (state.isToday || selected == null) {
            context.getString(R.string.pc_plan_today)
        } else {
            context.getString(R.string.pc_plan_for, "${selected.weekday} ${selected.date}")
        }
        val open = state.tasks.count { !it.done }
        timelineCount.visibility = if (state.tasks.isEmpty()) View.GONE else View.VISIBLE
        timelineCount.text = if (open == 0) {
            context.getString(R.string.pc_all_done_short)
        } else {
            context.resources.getQuantityString(R.plurals.pc_left_count, open, open)
        }
    }

    /** Keeps the bell's badge in step with the notification centre. */
    private fun observeUnread() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                InboxStore.get(context).unreadCount.collect { count ->
                    badge.visibility = if (count > 0) View.VISIBLE else View.GONE
                    badge.text = if (count > 9) "9+" else count.toString()
                    bell.contentDescription = if (count > 0) {
                        context.resources.getQuantityString(R.plurals.pc_a11y_unread, count, count)
                    } else {
                        context.getString(R.string.pc_a11y_notifications)
                    }
                }
            }
        }
    }

    /** Ticks only while the screen is visible; stops automatically when backgrounded. */
    private fun startTicker() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    nextUp.tick(now)
                    val minute = now / 60_000L
                    if (minute != lastMinute) {
                        lastMinute = minute
                        submitTimeline(now)
                        glance.refreshDelegation()
                    }
                    delay(1_000L - now % 1_000L)
                }
            }
        }
    }
}
