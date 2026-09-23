package com.example.petcare.ui

import com.example.petcare.R
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.design.*
import com.example.petcare.ui.home.LocalDayClock
import com.example.petcare.ui.home.TodayData
import com.example.petcare.ui.pets.PetListUi
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun dayLabel(day: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(day * 86_400_000L))

private fun CareTaskSummary.dueMillis() =
    LocalDayClock.dueMillis(dueDateEpochDay, reminderMinutesOfDay)

private fun CareTaskSummary.timeLabel() = String.format(
    Locale.getDefault(), "%02d:%02d", reminderMinutesOfDay / 60, reminderMinutesOfDay % 60
)

private fun CareTaskSummary.whenLabel() = "${dayLabel(dueDateEpochDay, "EEE d")} at ${timeLabel()}"
private fun List<CareTaskSummary>.progress(): Float =
    if (isEmpty()) 0f else count { it.isCompleted }.toFloat() / size

private fun PetEntity.speciesIcon() = when (species.lowercase(Locale.ROOT)) {
    "dog" -> R.drawable.pc_ic_dog
    "cat" -> R.drawable.pc_ic_cat
    else -> R.drawable.pc_ic_paw
}

private fun CareTaskSummary.categoryIcon(): Int {
    val words = "$category $title".lowercase(Locale.ROOT)
    return when {
        "vaccin" in words -> R.drawable.pc_ic_vaccine
        "brush" in words -> R.drawable.pc_ic_brush
        "medicat" in words || "pill" in words || "flea" in words -> R.drawable.pc_ic_pill
        "vet" in words || "health" in words -> R.drawable.pc_ic_stethoscope
        "groom" in words -> R.drawable.pc_ic_scissors
        "feed" in words || "breakfast" in words -> R.drawable.pc_ic_bowl
        "walk" in words || "exercise" in words -> R.drawable.pc_ic_walk
        else -> R.drawable.pc_ic_sparkles
    }
}

fun TodayData.toTodayUiState(
    userName: String,
    selectedDay: Long,
    selectedPetId: Long?,
    nowMillis: Long = System.currentTimeMillis(),
    today: Long = LocalDayClock.todayEpochDay(),
): TodayUiState {
    val all = (upcoming + completed).distinctBy { it.id }.sortedBy { it.dueMillis() }
    val dayTasks = all.filter {
        it.dueDateEpochDay == selectedDay ||
            (selectedDay == today && it.dueDateEpochDay < today && !it.isCompleted)
    }
    val selected = dayTasks.filter { selectedPetId == null || it.petId == selectedPetId }
    val tasks = selected.map { task ->
        val overdue = !task.isCompleted && task.dueMillis() < nowMillis && selectedDay == today
        TimelineItem.Task(task.id, task.dueMillis(),
            if (task.dueDateEpochDay < today && overdue) dayLabel(task.dueDateEpochDay, "EEE")
            else task.timeLabel(), task.title, task.petName, PetColor.fromIndex(task.petColorIndex),
            task.categoryIcon(), task.isCompleted, overdue)
    }
    val progress = ProgressSummary(tasks.map { if (it.done) it.petColor else null },
        tasks.count { it.done }, tasks.size)
    val overdueCount = tasks.count { it.overdue }
    val next = selected.firstOrNull { !it.isCompleted && it.dueMillis() > nowMillis }
    val nextLater = all.firstOrNull {
        !it.isCompleted && it.dueDateEpochDay > selectedDay &&
            (selectedPetId == null || it.petId == selectedPetId)
    }
    // With only overdue care remaining, show Due now rather than incorrectly claiming All done.
    val actionable = next ?: selected.firstOrNull { !it.isCompleted }
    val nextUp = when {
        selected.isEmpty() -> NextUpState.NothingPlanned(overdueCount)
        selectedDay != today -> selected.first().let {
            NextUpState.OtherDay(it.title, it.petName, PetColor.fromIndex(it.petColorIndex),
                it.whenLabel(), progress)
        }
        actionable != null -> NextUpState.Upcoming(actionable.title, actionable.petName,
            PetColor.fromIndex(actionable.petColorIndex), actionable.dueMillis(),
            actionable.timeLabel(), overdueCount, progress)
        else -> NextUpState.AllDone(nextLater?.let { "Next: ${it.title} ${it.whenLabel()}" }, progress)
    }
    val rings = buildList {
        add(PetRingItem("all", "All", R.drawable.pc_ic_paw, null, null,
            dayTasks.progress(), "${dayTasks.count { it.isCompleted }} of ${dayTasks.size}", selectedPetId == null))
        pets.forEach { pet ->
            val rows = dayTasks.filter { it.petId == pet.id }
            add(PetRingItem(pet.id.toString(), pet.name, pet.speciesIcon(), pet.photos().firstOrNull(),
                PetColor.fromIndex(pet.colorIndex), rows.progress(),
                "${rows.count { it.isCompleted }} of ${rows.size}", selectedPetId == pet.id))
        }
    }
    val weekStart = LocalDayClock.weekStart(selectedDay)
    val week = (0..6).map { index ->
        val day = weekStart + index
        WeekDay(dayLabel(day, "EEE"), dayLabel(day, "d"), all.any {
            it.dueDateEpochDay == day && (selectedPetId == null || it.petId == selectedPetId)
        }, day == selectedDay, day == today)
    }
    val firstName = userName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifBlank { "Pet parent" }
    val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..11 -> "Morning"; in 12..17 -> "Afternoon"; else -> "Evening" }
    return TodayUiState(dayLabel(selectedDay, "EEE d MMMM"), "$greeting, $firstName",
        firstName.take(1).uppercase(Locale.getDefault()), pets.isNotEmpty(), selectedDay == today,
        nextUp, rings, week, tasks)
}

fun PetListUi.toPetsUiState(today: Long = LocalDayClock.todayEpochDay()): PetsUiState {
    val cards = pets.map { pet ->
        val rows = tasks.filter { it.petId == pet.id && it.dueDateEpochDay == today }
        PetCardItem(pet.id, pet.name, pet.breed, pet.speciesIcon(), pet.photos().firstOrNull(),
            PetColor.fromIndex(pet.colorIndex), rows.progress(),
            "${rows.count { it.isCompleted }} of ${rows.size} today")
    }
    val comingUp = tasks.asSequence().filter { !it.isCompleted && it.dueDateEpochDay in (today + 1)..(today + 7) }
        .filter { task ->
            val words = "${task.category} ${task.title}".lowercase(Locale.ROOT)
            listOf("vet", "groom", "medicat", "health", "vaccin", "flea").any(words::contains)
        }.sortedBy { it.dueMillis() }.take(5).map { task ->
            ComingUpItem(task.id, dayLabel(task.dueDateEpochDay, "EEE"), dayLabel(task.dueDateEpochDay, "d"),
                task.title, task.petName, PetColor.fromIndex(task.petColorIndex), task.categoryIcon())
        }.toList()
    val monthStart = LocalDayClock.utcCalendar(today).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis / 86_400_000L
    val nextMonth = LocalDayClock.utcCalendar(monthStart).apply { add(Calendar.MONTH, 1) }.timeInMillis / 86_400_000L
    val monthly = expenses.filter { it.dateEpochDay >= monthStart && it.dateEpochDay < nextMonth }
    val currency = NumberFormat.getCurrencyInstance()
    val month = if (monthly.isEmpty()) null else MonthSummary(
        currency.format(monthly.sumOf { it.amountCents } / 100.0),
        monthly.groupBy { it.petId }.values.map { rows ->
            val amount = rows.sumOf { it.amountCents } / 100.0
            MonthShare(rows.first().petName, PetColor.fromIndex(rows.first().petColorIndex),
                amount, currency.format(amount))
        })
    return PetsUiState(cards, comingUp, month)
}
