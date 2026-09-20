package com.example.petcare.data.local.care

/** Small projection for the home screen widget. */
data class WidgetTaskRow(
    val id: Long,
    val title: String,
    val petName: String,
    val reminderMinutesOfDay: Int
)
