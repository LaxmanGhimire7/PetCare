package com.example.petcare.ui

import android.location.Location
import com.example.petcare.R
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.design.*
import com.example.petcare.location.PlaceLocation
import com.example.petcare.ui.home.LocalDayClock
import com.example.petcare.ui.home.TodayData
import com.example.petcare.ui.pets.PetListUi
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Converts Room summaries into the display models used by the Today, Pets, and Places binders. */
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

internal fun categoryIcon(category: String, title: String): Int {
    val words = "$category $title".lowercase(Locale.ROOT)
    return when {
        "vaccin" in words -> R.drawable.pc_ic_vaccine
        "medicat" in words || "pill" in words || "flea" in words -> R.drawable.pc_ic_pill
        "vet" in words || "health" in words -> R.drawable.pc_ic_stethoscope
        "groom" in words -> R.drawable.pc_ic_scissors
        "feed" in words || "breakfast" in words -> R.drawable.pc_ic_bowl
        "walk" in words || "exercise" in words -> R.drawable.pc_ic_walk
        else -> R.drawable.pc_ic_paw
    }
}

private fun CareTaskSummary.categoryIcon() = categoryIcon(category, title)

private fun ProviderEntity.isVet(): Boolean =
    "vet" in "$type $name".lowercase(Locale.ROOT)

fun TodayData.toTodayUiState(
    userName: String,
    selectedDay: Long,
    selectedPetId: Long?,
    lastLocation: Location? = null,
    nowMillis: Long = System.currentTimeMillis(),
    today: Long = LocalDayClock.todayEpochDay(),
): TodayUiState {
    // Keep overdue unfinished tasks visible on today, even when their original date has passed.
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
    val actionable = next ?: selected.asSequence()
        .filter { !it.isCompleted && it.dueMillis() <= nowMillis }
        .maxByOrNull { it.dueMillis() }
    val nextUp = when {
        selected.isEmpty() -> NextUpState.NothingPlanned(overdueCount)
        selectedDay != today -> selected.first().let {
            NextUpState.OtherDay(it.title, it.petName, PetColor.fromIndex(it.petColorIndex),
                it.whenLabel(), progress)
        }
        actionable != null -> NextUpState.Upcoming(actionable.title, actionable.petName,
            PetColor.fromIndex(actionable.petColorIndex), actionable.dueMillis(),
            actionable.timeLabel(), overdueCount, progress,
            dueFullLabel = if (actionable.dueMillis() <= nowMillis)
                "${dayLabel(actionable.dueDateEpochDay, "EEE")} ${actionable.timeLabel()}" else null)
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
        val rows = all.filter {
            it.dueDateEpochDay == day && (selectedPetId == null || it.petId == selectedPetId)
        }
        WeekDay(dayLabel(day, "EEE"), dayLabel(day, "d"), rows.isNotEmpty(),
            day == selectedDay, day == today, rows.progress())
    }
    val tallies = all.asSequence()
        .filter { it.dueDateEpochDay in (today - 59)..today }
        .groupBy { it.dueDateEpochDay }
        .map { (day, rows) -> DayTally(day, rows.count { it.isCompleted }, rows.size) }
    val healthTask = upcoming.asSequence()
        .filter { task ->
            val words = "${task.category} ${task.title}".lowercase(Locale.ROOT)
            listOf("vaccin", "vet", "medicat", "healthcare").any(words::contains)
        }
        .filter { it.dueMillis() in nowMillis..(nowMillis + 30L * 86_400_000L) }
        .minByOrNull { it.dueMillis() }
    val health = healthTask?.let { task ->
        HealthGlance(
            taskId = task.id,
            title = "${task.title} for ${task.petName}",
            whenLabel = "${dayLabel(task.dueDateEpochDay, "EEE d MMMM")}, ${task.timeLabel()}",
            daysAway = (task.dueDateEpochDay - today).toInt(),
            petColor = PetColor.fromIndex(task.petColorIndex),
            icon = task.categoryIcon(),
        )
    }
    val recentPlaceId = all.asSequence().sortedByDescending { it.id }
        .mapNotNull { it.placeId }.firstOrNull()
    val savedPlace = places.firstOrNull { it.isVet() && it.latitude != null && it.longitude != null }
        ?: places.firstOrNull { it.id == recentPlaceId && it.latitude != null && it.longitude != null }
        ?: places.firstOrNull { it.latitude != null && it.longitude != null }
    val place = savedPlace?.let { saved ->
        val latitude = requireNotNull(saved.latitude)
        val longitude = requireNotNull(saved.longitude)
        val detail = lastLocation?.let { origin ->
            String.format(Locale.getDefault(), "%.1f km away", PlaceLocation.distanceKm(
                origin.latitude, origin.longitude, latitude, longitude,
            ))
        } ?: saved.address.ifBlank { null }
        PlaceGlance(
            placeId = saved.id,
            label = if (saved.isVet()) "Your vet" else "Saved place",
            name = saved.name,
            detail = detail,
            phone = saved.phone.ifBlank { null },
            latitude = latitude,
            longitude = longitude,
        )
    }
    val glance = GlanceMath.summary(tallies, today).copy(health = health, place = place)
    val firstName = userName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifBlank { "Pet parent" }
    val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..11 -> "Morning"; in 12..17 -> "Afternoon"; else -> "Evening" }
    return TodayUiState(dayLabel(selectedDay, "EEE d MMMM"), "$greeting, $firstName",
        firstName.take(1).uppercase(Locale.getDefault()), pets.isNotEmpty(), selectedDay == today,
        nextUp, rings, week, tasks, glance)
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
