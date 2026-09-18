package com.example.petcare.data.local.pet

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pets")
data class PetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val species: String,
    val healthNotes: String = "",
    val photoUri: String? = null
)
