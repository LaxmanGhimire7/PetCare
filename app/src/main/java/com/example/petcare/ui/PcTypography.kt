package com.example.petcare.ui

import android.view.View
import android.view.ViewGroup
import android.util.TypedValue
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R
import java.util.WeakHashMap

/**
 * Re-applies the kit's exact text appearances after inflation. Some recent platform previews
 * re-apply the theme's default family after android:textAppearance while constructing TextViews;
 * applying the same appearance once the view exists preserves the font file named by the kit.
 */
object PcTypography {
    private val watchedLists = WeakHashMap<RecyclerView, Unit>()

    fun apply(root: View) {
        applyOne(root)
        if (root is RecyclerView && watchedLists.put(root, Unit) == null) {
            root.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) = apply(view)
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })
        }
        if (root is ViewGroup) for (index in 0 until root.childCount) apply(root.getChildAt(index))
    }

    private fun applyOne(view: View) {
        val text = view as? TextView ?: return
        val appearance = when (view.id) {
            R.id.pcTodayDate -> R.style.TextAppearance_PC_Supporting
            R.id.pcTodayGreeting -> R.style.TextAppearance_PC_Display
            R.id.pcTodayAvatar -> R.style.TextAppearance_PC_Avatar
            R.id.pcComingUpTitle, R.id.pcMonthTitle -> R.style.TextAppearance_PC_Section
            R.id.pcComingUpEmpty -> R.style.TextAppearance_PC_Supporting
            R.id.pcComingWeekday, R.id.pcComingPet, R.id.pcTimelinePetName,
            R.id.pcDayName, R.id.pcNextUpProgress -> R.style.TextAppearance_PC_Meta
            R.id.pcComingDate -> R.style.TextAppearance_PC_FigureDate
            R.id.pcComingTitle, R.id.pcTimelineTitle -> R.style.TextAppearance_PC_RowTitle
            R.id.pcNowTime -> R.style.TextAppearance_PC_FigureStrong
            R.id.pcPetName -> if (text.textSize > TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 23.5f, text.resources.displayMetrics))
                R.style.TextAppearance_PC_Headline else R.style.TextAppearance_PC_CardTitle
            R.id.pcPetBreed, R.id.pcNextUpUntil -> R.style.TextAppearance_PC_Supporting
            R.id.pcPetToday, R.id.pcTimelineOverdue, R.id.pcNextUpOverdue,
            R.id.pcNextUpPet -> R.style.TextAppearance_PC_MetaStrong
            R.id.pcRingName -> R.style.TextAppearance_PC_Label
            R.id.pcRingCount -> R.style.TextAppearance_PC_FigureSmall
            R.id.pcTimelineTime -> R.style.TextAppearance_PC_Figure
            R.id.pcDayDate -> R.style.TextAppearance_PC_FigureLarge
            R.id.pcMonthTotal -> R.style.TextAppearance_PC_Amount
            R.id.pcNextUpLabel -> R.style.TextAppearance_PC_LabelStrong
            R.id.pcNextUpTitle -> R.style.TextAppearance_PC_Headline
            R.id.pcNextUpCountdown -> R.style.TextAppearance_PC_Countdown
            else -> if (view.id != View.NO_ID) 0 else when (text.text.toString()) {
                text.context.getString(R.string.pc_your_pets) -> R.style.TextAppearance_PC_Display
                text.context.getString(R.string.pc_add_pet) -> R.style.TextAppearance_PC_RowTitle
                text.context.getString(R.string.pc_now) -> R.style.TextAppearance_PC_MetaStrong
                text.context.getString(R.string.pc_spent_on_care) -> R.style.TextAppearance_PC_Supporting
                text.context.getString(R.string.pc_no_spending),
                text.context.getString(R.string.pc_empty_body) -> R.style.TextAppearance_PC_Body
                text.context.getString(R.string.pc_timeline_empty_title) -> R.style.TextAppearance_PC_RowTitle
                text.context.getString(R.string.pc_timeline_empty_body) -> R.style.TextAppearance_PC_Meta
                text.context.getString(R.string.pc_empty_title) -> R.style.TextAppearance_PC_Headline
                else -> 0
            }
        }
        if (appearance != 0) TextViewCompat.setTextAppearance(text, appearance)
    }
}
