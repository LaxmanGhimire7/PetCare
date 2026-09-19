package com.example.petcare.data.local.care

data class CareTaskSummary(
    val id: Long,
    val petId: Long,
    val title: String,
    val dueDateEpochDay: Long,
    val reminderMinutesOfDay: Int,
    val petName: String,
    val petColorIndex: Int,
    val category: String,
    val frequency: String,
    val requiredSupplies: String,
    val notes: String,
    val latitude: Double?,
    val longitude: Double?,
    val placeId: Long?
)
