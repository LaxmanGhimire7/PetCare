package com.example.petcare.design

import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.FragmentManager
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.example.petcare.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class Repeat { ONCE, DAILY, WEEKLY, MONTHLY }

data class EditorPet(
    val id: Long,
    val name: String,
    val petColor: PetColor,
    @param:DrawableRes val icon: Int,
    val photoUri: String?,
)

/** [key] is whatever your database stores for the category, e.g. "FEEDING". */
data class EditorCategory(val key: String, val label: String, @param:DrawableRes val icon: Int)

data class EditorPlace(val id: Long, val name: String)

/** What the editor hands back on Save. [id] is null for a new task. */
data class TaskDraft(
    val id: Long?,
    val title: String,
    val petId: Long?,
    val categoryKey: String?,
    val dueAtMillis: Long?,
    val repeat: Repeat,
    val placeId: Long?,
    val supplies: String?,
    val notes: String?,
)

interface TaskEditorActions {
    /** Already validated: title, pet, category and date are all present. */
    fun onSave(draft: TaskDraft)
    fun onClose()
}

/**
 * Binds pc_fragment_task_editor.xml. Shows its own Material date and time pickers,
 * validates inline, and returns a [TaskDraft]. Call [setup] once in onViewCreated.
 */
class TaskEditorBinder(
    root: View,
    private val fragmentManager: FragmentManager,
    private val actions: TaskEditorActions,
) {
    private val context = root.context
    private val scroll: NestedScrollView = root.findViewById(R.id.pcEditorScroll)
    private val title: TextView = root.findViewById(R.id.pcEditorTitle)
    private val name: EditText = root.findViewById(R.id.pcEditorName)
    private val nameLine: View = root.findViewById(R.id.pcEditorNameLine)
    private val nameError: TextView = root.findViewById(R.id.pcEditorNameError)
    private val petsBox: LinearLayout = root.findViewById(R.id.pcEditorPets)
    private val petError: TextView = root.findViewById(R.id.pcEditorPetError)
    private val categories: ChipGroup = root.findViewById(R.id.pcEditorCategories)
    private val categoryError: TextView = root.findViewById(R.id.pcEditorCategoryError)
    private val dateValue: TextView = root.findViewById(R.id.pcEditorDate)
    private val timeValue: TextView = root.findViewById(R.id.pcEditorTime)
    private val dateError: TextView = root.findViewById(R.id.pcEditorDateError)
    private val repeat: MaterialButtonToggleGroup = root.findViewById(R.id.pcEditorRepeat)
    private val place: MaterialAutoCompleteTextView = root.findViewById(R.id.pcEditorPlace)
    private val supplies: EditText = root.findViewById(R.id.pcEditorSupplies)
    private val notes: EditText = root.findViewById(R.id.pcEditorNotes)
    private val save: MaterialButton = root.findViewById(R.id.pcEditorSave)

    private var taskId: Long? = null
    private var pets: List<EditorPet> = emptyList()
    private var places: List<EditorPlace> = emptyList()
    private var petId: Long? = null
    private var categoryKey: String? = null
    private var year = -1
    private var month = -1
    private var day = -1
    private var hour = 9
    private var minute = 0
    private var placeId: Long? = null

    init {
        root.applySystemBarPaddingWithKeyboard()
        root.findViewById<View>(R.id.pcEditorClose).setOnClickListener { actions.onClose() }
        root.findViewById<View>(R.id.pcEditorDateRow).setOnClickListener { pickDate() }
        root.findViewById<View>(R.id.pcEditorTimeRow).setOnClickListener { pickTime() }
        name.afterTextChanged { if (it.isNotBlank()) showError(nameError, null) }
        categories.setOnCheckedStateChangeListener { group, checkedIds ->
            categoryKey = checkedIds.firstOrNull()?.let { id -> group.findViewById<Chip>(id)?.tag as? String }
            if (categoryKey != null) showError(categoryError, null)
        }
        place.setOnItemClickListener { _, _, position, _ -> placeId = if (position == 0) null else places.getOrNull(position - 1)?.id }
        save.setOnClickListener { submit() }
    }

    /**
     * [initial] = the task being edited, or null for a new task. [preselectPetId] picks a pet
     * for new tasks (e.g. the pet filtered on Today). New tasks default to 09:00.
     */
    fun setup(
        pets: List<EditorPet>,
        categories: List<EditorCategory>,
        places: List<EditorPlace>,
        initial: TaskDraft? = null,
        preselectPetId: Long? = null,
    ) {
        this.pets = pets
        this.places = places
        taskId = initial?.id
        title.setText(if (initial?.id != null) R.string.pc_edit_task_title else R.string.pc_new_task_title)
        name.setText(initial?.title.orEmpty())

        petId = initial?.petId ?: preselectPetId ?: pets.singleOrNull()?.id
        buildPets()

        categoryKey = initial?.categoryKey
        buildCategories(categories)

        initial?.dueAtMillis?.let { millis ->
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            year = c.get(Calendar.YEAR)
            month = c.get(Calendar.MONTH)
            day = c.get(Calendar.DAY_OF_MONTH)
            hour = c.get(Calendar.HOUR_OF_DAY)
            minute = c.get(Calendar.MINUTE)
        }
        renderDate()
        renderTime()

        repeat.check(
            when (initial?.repeat ?: Repeat.ONCE) {
                Repeat.ONCE -> R.id.pcRepeatOnce
                Repeat.DAILY -> R.id.pcRepeatDaily
                Repeat.WEEKLY -> R.id.pcRepeatWeekly
                Repeat.MONTHLY -> R.id.pcRepeatMonthly
            },
        )

        val labels = listOf(context.getString(R.string.pc_no_place)) + places.map { it.name }
        place.setAdapter(ArrayAdapter(context, R.layout.pc_item_dropdown, labels))
        placeId = initial?.placeId?.takeIf { id -> places.any { it.id == id } }
        place.setText(places.firstOrNull { it.id == placeId }?.name ?: labels.first(), false)

        supplies.setText(initial?.supplies.orEmpty())
        notes.setText(initial?.notes.orEmpty())
    }

    /** Disable Save while your ViewModel writes to the database. */
    fun setSaving(saving: Boolean) {
        save.isEnabled = !saving
    }

    private fun buildPets() {
        petsBox.removeAllViews()
        val inflater = LayoutInflater.from(context)
        pets.forEach { pet ->
            val item = inflater.inflate(R.layout.pc_item_editor_pet, petsBox, false)
            item.tag = pet.id
            item.setOnClickListener {
                Haptics.tick(it)
                petId = pet.id
                showError(petError, null)
                renderPets()
            }
            petsBox.addView(item)
        }
        renderPets()
    }

    private fun renderPets() {
        for (index in 0 until petsBox.childCount) {
            val item = petsBox.getChildAt(index)
            val pet = pets.firstOrNull { it.id == item.tag } ?: continue
            val chosen = pet.id == petId
            val main = pet.petColor.main(context)
            val ring: PetRingView = item.findViewById(R.id.pcChoiceRing)
            val icon: ImageView = item.findViewById(R.id.pcChoiceIcon)
            val label: TextView = item.findViewById(R.id.pcChoiceName)
            ring.setRingColor(main)
            ring.setProgress(if (chosen) 1f else 0f, animate = chosen)
            if (pet.photoUri.isNullOrBlank()) {
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                icon.clipToOutline = false
                val padding = context.resources.getDimensionPixelSize(R.dimen.space_8)
                icon.setPadding(padding, padding, padding, padding)
                icon.setImageResource(pet.icon)
                ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(main))
                ViewCompat.setBackgroundTintList(icon, ColorStateList.valueOf(pet.petColor.tint(context)))
            } else {
                icon.scaleType = ImageView.ScaleType.CENTER_CROP
                icon.clipToOutline = true
                icon.setPadding(0, 0, 0, 0)
                ImageViewCompat.setImageTintList(icon, null)
                ViewCompat.setBackgroundTintList(icon, null)
                icon.load(pet.photoUri) {
                    crossfade(true)
                    fallback(pet.icon)
                    error(pet.icon)
                }
            }
            label.text = pet.name
            TextViewCompat.setTextAppearance(label, if (chosen) R.style.TextAppearance_PC_LabelBold else R.style.TextAppearance_PC_Label)
            label.setTextColor(ContextCompat.getColor(context, if (chosen) R.color.pc_text_primary else R.color.pc_text_secondary))
            item.isSelected = chosen
            item.contentDescription = pet.name
        }
    }

    private fun buildCategories(list: List<EditorCategory>) {
        categories.removeAllViews()
        list.forEach { category ->
            val chip = Chip(context).apply {
                id = View.generateViewId()
                tag = category.key
                text = category.label
                isCheckable = true
                setChipIconResource(category.icon)
                isChipIconVisible = true
            }
            categories.addView(chip)
            if (category.key == categoryKey) chip.isChecked = true
        }
    }

    private fun pickDate() {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        if (year >= 0) utc.set(year, month, day, 0, 0, 0) else {
            val now = Calendar.getInstance()
            utc.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }
        utc.set(Calendar.MILLISECOND, 0)
        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pc_pick_date)
            .setSelection(utc.timeInMillis)
        if (taskId == null) {
            builder.setCalendarConstraints(
                CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now()).build(),
            )
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection }
            year = picked.get(Calendar.YEAR)
            month = picked.get(Calendar.MONTH)
            day = picked.get(Calendar.DAY_OF_MONTH)
            showError(dateError, null)
            renderDate()
        }
        picker.show(fragmentManager, "pc_date")
    }

    private fun pickTime() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(R.string.pc_pick_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            hour = picker.hour
            minute = picker.minute
            renderTime()
        }
        picker.show(fragmentManager, "pc_time")
    }

    private fun renderDate() {
        if (year < 0) {
            dateValue.setText(R.string.pc_choose)
            dateValue.setTextColor(ContextCompat.getColor(context, R.color.pc_text_secondary))
        } else {
            dateValue.text = SimpleDateFormat("EEE d MMMM", Locale.getDefault()).format(localDue(0, 0).time)
            dateValue.setTextColor(ContextCompat.getColor(context, R.color.pc_text_primary))
        }
    }

    private fun renderTime() {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        timeValue.text = DateFormat.getTimeFormat(context).format(c.time)
    }

    private fun localDue(h: Int, m: Int): Calendar = Calendar.getInstance().apply {
        set(year, month, day, h, m, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun submit() {
        var firstError: View? = null
        fun check(ok: Boolean, error: TextView, message: Int, anchor: View) {
            showError(error, if (ok) null else context.getString(message))
            if (!ok && firstError == null) firstError = anchor
        }
        check(name.value().isNotEmpty(), nameError, R.string.pc_err_task_title, name)
        check(petId != null, petError, R.string.pc_err_pet, petError)
        check(categoryKey != null, categoryError, R.string.pc_err_category, categoryError)
        check(year >= 0, dateError, R.string.pc_err_date, dateError)
        firstError?.let { view ->
            scroll.smoothScrollTo(0, maxOf(0, view.top - SCROLL_MARGIN_PX))
            if (view === name) name.requestFocus()
            return
        }

        Haptics.confirm(save)
        actions.onSave(
            TaskDraft(
                id = taskId,
                title = name.value(),
                petId = petId,
                categoryKey = categoryKey,
                dueAtMillis = localDue(hour, minute).timeInMillis,
                repeat = when (repeat.checkedButtonId) {
                    R.id.pcRepeatDaily -> Repeat.DAILY
                    R.id.pcRepeatWeekly -> Repeat.WEEKLY
                    R.id.pcRepeatMonthly -> Repeat.MONTHLY
                    else -> Repeat.ONCE
                },
                placeId = placeId,
                supplies = supplies.value().ifEmpty { null },
                notes = notes.value().ifEmpty { null },
            ),
        )
    }

    private fun showError(view: TextView, message: String?) {
        view.text = message
        view.visibility = if (message == null) View.GONE else View.VISIBLE
        if (view === nameError) {
            nameLine.setBackgroundColor(ContextCompat.getColor(context, if (message == null) R.color.pc_hairline else R.color.pc_error))
        }
    }

    private companion object {
        const val SCROLL_MARGIN_PX = 120
    }
}
