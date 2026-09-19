package com.example.petcare.data.local.expense

data class ExpenseSummary(
    val id: Long,
    val petId: Long,
    val category: String,
    val amountCents: Long,
    val dateEpochDay: Long,
    val note: String,
    val petName: String,
    val petColorIndex: Int
)
