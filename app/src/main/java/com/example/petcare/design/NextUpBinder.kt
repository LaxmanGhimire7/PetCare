package com.example.petcare.design

import android.content.Context
import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.example.petcare.R
import java.util.Locale

/** Today's progress: one entry per task, the pet colour when done, null when still open. */
data class ProgressSummary(val segments: List<PetColor?>, val done: Int, val total: Int)

/** Every state the Next-up card can be in. Your ViewModel picks one. */
sealed interface NextUpState {
    /** The earliest open task after now. [dueLabel] is "HH:mm". */
    data class Upcoming(
        val title: String,
        val petName: String,
        val petColor: PetColor,
        val dueAtMillis: Long,
        val dueLabel: String,
        val overdueCount: Int,
        val progress: ProgressSummary,
    ) : NextUpState

    /** Another day is selected on the week strip: show its first task, no countdown. */
    data class OtherDay(
        val title: String,
        val petName: String,
        val petColor: PetColor,
        val whenLabel: String,
        val progress: ProgressSummary,
    ) : NextUpState

    /** Everything today is done. [nextLabel] e.g. "Next: Morning feed tomorrow at 08:00". */
    data class AllDone(val nextLabel: String?, val progress: ProgressSummary) : NextUpState

    /** No tasks on the selected day. */
    data class NothingPlanned(val overdueCount: Int) : NextUpState
}

/** Formats the countdown: colons in orange, seconds in grey, tabular digits. */
object Countdown {

    fun format(context: Context, remainingMillis: Long): CharSequence {
        val primary = ContextCompat.getColor(context, R.color.pc_primary_text)
        if (remainingMillis <= 0L) {
            val text = context.getString(R.string.pc_due_now)
            return SpannableString(text).apply {
                setSpan(ForegroundColorSpan(primary), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val totalSeconds = remainingMillis / 1000L
        val hours = (totalSeconds / 3600L).coerceAtMost(99L)
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        val secondary = ContextCompat.getColor(context, R.color.pc_text_secondary)
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(primary), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(primary), 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(secondary), 6, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** What TalkBack reads: "Brushing in 57 minutes", not a string of digits. */
    fun describe(context: Context, title: String, remainingMillis: Long): String {
        if (remainingMillis <= 0L) return context.getString(R.string.pc_a11y_due_now, title)
        val totalMinutes = ((remainingMillis + 59_999L) / 60_000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val res = context.resources
        val parts = buildList {
            if (hours > 0) add(res.getQuantityString(R.plurals.pc_hours, hours, hours))
            if (minutes > 0 || hours == 0) add(res.getQuantityString(R.plurals.pc_minutes, minutes, minutes))
        }.joinToString(" ")
        return context.getString(R.string.pc_a11y_countdown, title, parts)
    }
}

/** Binds the Next-up card (pc_view_next_up.xml). */
class NextUpBinder(
    root: View,
    private val onDone: () -> Unit,
    private val onSnooze: () -> Unit,
    private val onPlan: () -> Unit,
    private val onRoutine: () -> Unit = {},
) {
    private val context = root.context
    private val label: TextView = root.findViewById(R.id.pcNextUpLabel)
    private val overdue: TextView = root.findViewById(R.id.pcNextUpOverdue)
    private val pet: TextView = root.findViewById(R.id.pcNextUpPet)
    private val title: TextView = root.findViewById(R.id.pcNextUpTitle)
    private val countdown: TextView = root.findViewById(R.id.pcNextUpCountdown)
    private val until: TextView = root.findViewById(R.id.pcNextUpUntil)
    private val bar: SegmentedBarView = root.findViewById(R.id.pcNextUpBar)
    private val progressLabel: TextView = root.findViewById(R.id.pcNextUpProgress)
    private val actions: View = root.findViewById(R.id.pcNextUpActions)
    private val done: MaterialButton = root.findViewById(R.id.pcNextUpDone)
    private val snooze: MaterialButton = root.findViewById(R.id.pcNextUpSnooze)
    private val plan: MaterialButton = root.findViewById(R.id.pcNextUpPlan)
    private val routine: MaterialButton = root.findViewById(R.id.pcNextUpRoutine)

    private var state: NextUpState? = null

    init {
        done.setOnClickListener {
            Haptics.confirm(it)
            onDone()
        }
        snooze.setOnClickListener { onSnooze() }
        plan.setOnClickListener { onPlan() }
        routine.setOnClickListener { onRoutine() }
    }

    fun render(newState: NextUpState, animateBar: Boolean) {
        state = newState
        when (newState) {
            is NextUpState.Upcoming -> {
                label.setText(R.string.pc_next_up)
                showOverdue(newState.overdueCount)
                showPet(newState.petName, newState.petColor)
                title.text = newState.title
                countdown.visibility = View.VISIBLE
                until.visibility = View.VISIBLE
                until.text = context.getString(R.string.pc_until, newState.dueLabel)
                showProgress(newState.progress, animateBar)
                showActions(done = true, snooze = true, plan = false)
            }
            is NextUpState.OtherDay -> {
                label.setText(R.string.pc_first_up)
                showOverdue(0)
                showPet(newState.petName, newState.petColor)
                title.text = newState.title
                countdown.visibility = View.GONE
                until.visibility = View.VISIBLE
                until.text = newState.whenLabel
                showProgress(newState.progress, animateBar)
                showActions(done = false, snooze = false, plan = false)
            }
            is NextUpState.AllDone -> {
                label.setText(R.string.pc_today)
                showOverdue(0)
                pet.visibility = View.GONE
                title.setText(R.string.pc_all_done)
                countdown.visibility = View.GONE
                until.visibility = if (newState.nextLabel != null) View.VISIBLE else View.GONE
                until.text = newState.nextLabel
                showProgress(newState.progress, animateBar)
                showActions(done = false, snooze = false, plan = false)
            }
            is NextUpState.NothingPlanned -> {
                label.setText(R.string.pc_today)
                showOverdue(newState.overdueCount)
                pet.visibility = View.GONE
                title.setText(R.string.pc_nothing_planned)
                countdown.visibility = View.GONE
                until.visibility = View.GONE
                bar.visibility = View.GONE
                progressLabel.visibility = View.GONE
                showActions(done = false, snooze = false, plan = true)
            }
        }
    }

    /** Called every second by TodayScreenBinder. Recalculated from the clock, never decremented. */
    fun tick(nowMillis: Long) {
        val upcoming = state as? NextUpState.Upcoming ?: return
        val remaining = upcoming.dueAtMillis - nowMillis
        countdown.text = Countdown.format(context, remaining)
        countdown.contentDescription = Countdown.describe(context, upcoming.title, remaining)
    }

    private fun showOverdue(count: Int) {
        overdue.visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) overdue.text = context.resources.getQuantityString(R.plurals.pc_overdue_count, count, count)
    }

    private fun showPet(name: String, color: PetColor) {
        pet.visibility = View.VISIBLE
        pet.text = name
        pet.setTextColor(color.main(context))
        ViewCompat.setBackgroundTintList(pet, ColorStateList.valueOf(color.tint(context)))
    }

    private fun showProgress(progress: ProgressSummary, animate: Boolean) {
        val visible = progress.total > 0
        bar.visibility = if (visible) View.VISIBLE else View.GONE
        progressLabel.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        bar.setEqualSegments(progress.segments.map { it?.main(context) }, animate)
        progressLabel.text = context.getString(R.string.pc_done_of_total, progress.done, progress.total)
    }

    private fun showActions(done: Boolean, snooze: Boolean, plan: Boolean) {
        actions.visibility = if (done || snooze || plan) View.VISIBLE else View.GONE
        this.done.visibility = if (done) View.VISIBLE else View.GONE
        this.snooze.visibility = if (snooze) View.VISIBLE else View.GONE
        this.plan.visibility = if (plan) View.VISIBLE else View.GONE
        routine.visibility = if (plan) View.VISIBLE else View.GONE
    }
}
