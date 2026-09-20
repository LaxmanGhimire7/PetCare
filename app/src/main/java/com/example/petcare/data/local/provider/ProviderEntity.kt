package com.example.petcare.data.local.provider

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "providers")
/** A saved care place with optional map, hours, and contact details. */
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ownerId: Long = 1,
    val name: String,
    val type: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val openingHours: String = "",
    val phone: String = "",
    val bookingUrl: String = ""
)
