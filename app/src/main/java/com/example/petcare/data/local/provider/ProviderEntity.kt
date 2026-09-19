package com.example.petcare.data.local.provider

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val openingHours: String = "",
    val phone: String = "",
    val bookingUrl: String = ""
)
