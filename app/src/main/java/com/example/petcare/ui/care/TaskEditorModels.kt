package com.example.petcare.ui.care

import android.content.res.Resources
import com.example.petcare.R
import com.example.petcare.data.local.care.CARE_FREQUENCY_DAILY
import com.example.petcare.data.local.care.CARE_FREQUENCY_MONTHLY
import com.example.petcare.data.local.care.CARE_FREQUENCY_ONE_TIME
import com.example.petcare.data.local.care.CARE_FREQUENCY_WEEKLY
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.design.EditorCategory
import com.example.petcare.design.EditorPet
import com.example.petcare.design.EditorPlace
import com.example.petcare.design.PetColor
import com.example.petcare.design.Repeat
import com.example.petcare.design.TaskDraft
import com.example.petcare.ui.categoryIcon
import com.example.petcare.ui.home.LocalDayClock
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal fun PetEntity.toEditorPet() = EditorPet(
    id = id,
    name = name,
    petColor = PetColor.fromIndex(colorIndex),
    icon = when (species.lowercase(Locale.ROOT)) {
        "dog" -> R.drawable.pc_ic_dog
        "cat" -> R.drawable.pc_ic_cat
        else -> R.drawable.pc_ic_paw
    },
)

internal fun ProviderEntity.toEditorPlace() = EditorPlace(id, name)

internal fun Resources.editorCategories(): List<EditorCategory> =
    getStringArray(R.array.care_categories).map { label ->
        EditorCategory(label, label, categoryIcon(label, ""))
    }

internal fun CareTaskEntity.toTaskDraft() = TaskDraft(
    id = id,
    title = title,
    petId = petId,
    categoryKey = category,
    dueAtMillis = LocalDayClock.dueMillis(dueDateEpochDay, reminderMinutesOfDay),
    repeat = frequency.toRepeat(),
    placeId = placeId,
    supplies = requiredSupplies.ifBlank { null },
    notes = notes.ifBlank { null },
)

internal fun String.toRepeat() = when (this) {
    CARE_FREQUENCY_DAILY -> Repeat.DAILY
    CARE_FREQUENCY_WEEKLY -> Repeat.WEEKLY
    CARE_FREQUENCY_MONTHLY -> Repeat.MONTHLY
    else -> Repeat.ONCE
}

internal fun Repeat.toFrequency() = when (this) {
    Repeat.DAILY -> CARE_FREQUENCY_DAILY
    Repeat.WEEKLY -> CARE_FREQUENCY_WEEKLY
    Repeat.MONTHLY -> CARE_FREQUENCY_MONTHLY
    Repeat.ONCE -> CARE_FREQUENCY_ONE_TIME
}

internal fun taskDueParts(dueAtMillis: Long): Pair<Long, Int> {
    val local = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
    val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis / 86_400_000L
    val minutes = local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE)
    return day to minutes
}
