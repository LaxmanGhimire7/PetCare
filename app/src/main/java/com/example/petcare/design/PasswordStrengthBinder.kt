package com.example.petcare.design

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.example.petcare.R

/**
 * Live password meter (pc_view_password_strength.xml): four segments, a label,
 * and a checklist that ticks each rule as it's met.
 */
class PasswordStrengthBinder(
    root: View,
    private val password: EditText,
    private val personal: () -> List<String?> = { emptyList() },
) {
    private val context = root.context
    private val bar: SegmentedBarView = root.findViewById(R.id.pcStrengthBar)
    private val label: TextView = root.findViewById(R.id.pcStrengthLabel)
    private val rulesBox: LinearLayout = root.findViewById(R.id.pcStrengthRules)
    private val rows = linkedMapOf<PasswordRule, Pair<ImageView, TextView>>()

    /** The latest evaluation; check `current.acceptable` before submitting. */
    var current: PasswordCheck = PasswordPolicy.check("")
        private set

    init {
        val inflater = LayoutInflater.from(context)
        PasswordRule.entries.forEach { rule ->
            val row = inflater.inflate(R.layout.pc_item_password_rule, rulesBox, false)
            val text = row.findViewById<TextView>(R.id.pcRuleText)
            text.setText(rule.label)
            rulesBox.addView(row)
            rows[rule] = row.findViewById<ImageView>(R.id.pcRuleIcon) to text
        }
        password.afterTextChanged { update() }
        update()
    }

    /** Re-evaluates; call after the name or email changes too. */
    fun update() {
        val text = password.text?.toString().orEmpty()
        current = PasswordPolicy.check(text, personal())
        val empty = text.isEmpty()
        val color = ContextCompat.getColor(context, colorFor(current.score))
        val filled = if (empty) 0 else current.score.coerceAtLeast(1)
        bar.setEqualSegments(List(SEGMENTS) { index -> if (index < filled) color else null }, animate = false)

        label.text = if (empty) "" else context.getString(labelFor(current.score))
        label.setTextColor(color)

        val metColor = ContextCompat.getColor(context, R.color.pc_success)
        val openColor = ContextCompat.getColor(context, R.color.pc_track)
        rows.forEach { (rule, views) ->
            val met = !empty && rule in current.met
            views.first.setImageResource(R.drawable.pc_ic_circle_check)
            ImageViewCompat.setImageTintList(views.first, ColorStateList.valueOf(if (met) metColor else openColor))
            views.second.setTextColor(
                ContextCompat.getColor(context, if (met) R.color.pc_text_primary else R.color.pc_text_secondary),
            )
        }
    }

    private fun colorFor(score: Int): Int = when {
        score <= 1 -> R.color.pc_error
        score == 2 -> R.color.pc_warning
        else -> R.color.pc_success
    }

    private fun labelFor(score: Int): Int = when (score) {
        0, 1 -> R.string.pc_strength_weak
        2 -> R.string.pc_strength_fair
        3 -> R.string.pc_strength_good
        else -> R.string.pc_strength_strong
    }

    private companion object {
        const val SEGMENTS = 4
    }
}
