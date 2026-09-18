package com.example.petcare.data.local.care

data class CareTaskSummary(
    val id: Long,
    val petId: Long,
    val title: String,
    val dueDateEpochDay: Long,
    val petName: String
)
