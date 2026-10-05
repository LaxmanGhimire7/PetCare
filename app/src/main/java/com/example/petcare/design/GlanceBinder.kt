package com.example.petcare.design

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import com.example.petcare.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Binds pc_view_glance.xml. The delegation card reads [DelegationLog] itself,
 * so it appears as soon as a checklist is shared, with no ViewModel changes.
 */
class GlanceBinder(
    private val root: View,
    private val onOpenTask: (taskId: Long) -> Unit,
    private val onShareAgain: () -> Unit,
    private val onOpenPlace: (placeId: Long) -> Unit,
) {
    private val context = root.context
    private val streak: TextView = root.findViewById(R.id.pcGlanceStreak)
    private val streakBody: TextView = root.findViewById(R.id.pcGlanceStreakBody)
    private val week: TextView = root.findViewById(R.id.pcGlanceWeek)
    private val bars: MiniBarsView = root.findViewById(R.id.pcGlanceBars)
    private val health: View = root.findViewById(R.id.pcGlanceHealth)
    private val healthIcon: ImageView = root.findViewById(R.id.pcGlanceHealthIcon)
    private val healthTitle: TextView = root.findViewById(R.id.pcGlanceHealthTitle)
    private val healthWhen: TextView = root.findViewById(R.id.pcGlanceHealthWhen)
    private val healthDays: TextView = root.findViewById(R.id.pcGlanceHealthDays)
    private val healthUnit: TextView = root.findViewById(R.id.pcGlanceHealthUnit)
    private val delegation: View = root.findViewById(R.id.pcGlanceDelegation)
    private val delegationTitle: TextView = root.findViewById(R.id.pcGlanceDelegationTitle)
    private val delegationWhen: TextView = root.findViewById(R.id.pcGlanceDelegationWhen)
    private val place: View = root.findViewById(R.id.pcGlancePlace)
    private val placeLabel: TextView = root.findViewById(R.id.pcGlancePlaceLabel)
    private val placeName: TextView = root.findViewById(R.id.pcGlancePlaceName)
    private val placeDetail: TextView = root.findViewById(R.id.pcGlancePlaceDetail)
    private val placeCall: View = root.findViewById(R.id.pcGlancePlaceCall)
    private val placeDirections: View = root.findViewById(R.id.pcGlancePlaceDirections)
    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

    init {
        root.findViewById<View>(R.id.pcGlanceShareAgain).setOnClickListener { onShareAgain() }
    }

    fun render(state: GlanceState) {
        val res = context.resources
        streak.text = res.getQuantityString(R.plurals.pc_streak_days, state.streakDays, state.streakDays)
        streakBody.setText(if (state.streakDays > 0) R.string.pc_streak_body else R.string.pc_streak_start)

        week.text = state.weekPercent?.let { context.getString(R.string.pc_week_percent, it) }
            ?: context.getString(R.string.pc_week_empty)
        bars.setValues(state.weekDays.ifEmpty { List(7) { null } }, state.todayIndex)

        bindHealth(state.health)
        bindPlace(state.place)
        refreshDelegation()
    }

    /** Also called every minute by TodayScreenBinder, so a new hand-over shows promptly. */
    fun refreshDelegation() {
        val sent = DelegationLog.today(context)
        delegation.visibility = if (sent != null) View.VISIBLE else View.GONE
        if (sent != null) {
            delegationTitle.text = context.getString(R.string.pc_delegation_title, sent.first)
            delegationWhen.text = context.getString(R.string.pc_delegation_when, clock.format(Date(sent.second)))
        }
    }

    private fun bindHealth(item: HealthGlance?) {
        health.visibility = if (item != null) View.VISIBLE else View.GONE
        if (item == null) return
        healthIcon.setImageResource(item.icon)
        ViewCompat.setBackgroundTintList(healthIcon, ColorStateList.valueOf(item.petColor.tint(context)))
        ImageViewCompat.setImageTintList(healthIcon, ColorStateList.valueOf(item.petColor.main(context)))
        healthTitle.text = item.title
        healthWhen.text = item.whenLabel
        if (item.daysAway <= 0) {
            healthDays.visibility = View.GONE
            healthUnit.setText(R.string.pc_health_today)
        } else {
            healthDays.visibility = View.VISIBLE
            healthDays.text = item.daysAway.toString()
            healthUnit.text = context.resources.getQuantityString(R.plurals.pc_days_unit, item.daysAway)
        }
        health.setOnClickListener { onOpenTask(item.taskId) }
    }

    private fun bindPlace(item: PlaceGlance?) {
        place.visibility = if (item != null) View.VISIBLE else View.GONE
        if (item == null) return
        placeLabel.text = item.label
        placeName.text = item.name
        placeDetail.visibility = if (item.detail.isNullOrBlank()) View.GONE else View.VISIBLE
        placeDetail.text = item.detail
        placeCall.visibility = if (item.phone.isNullOrBlank()) View.GONE else View.VISIBLE
        placeCall.setOnClickListener { item.phone?.let { PlaceIntents.call(context, it) } }
        placeDirections.setOnClickListener { PlaceIntents.directions(context, item.latitude, item.longitude, item.name) }
        place.setOnClickListener { onOpenPlace(item.placeId) }
    }
}
