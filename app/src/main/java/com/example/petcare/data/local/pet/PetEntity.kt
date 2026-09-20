package com.example.petcare.data.local.pet

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "pets")
/** Persisted pet profile with a stable identity colour and local photo references. */
data class PetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ownerId: Long = 1,
    val name: String,
    val species: String,
    val colorIndex: Int = 0,
    val breed: String = "",
    val age: String = "",
    val weight: String = "",
    val dietaryPreferences: String = "",
    val vaccinationHistory: String = "",
    val allergies: String = "",
    val favoriteToys: String = "",
    val medicalRecords: String = "",
    val groomingRoutine: String = "",
    val healthNotes: String = "",
    val photoUri: String? = null,
    val photoUris: String = ""
) {
    fun photos(): List<String> = photoUris
        .split(PHOTO_SEPARATOR)
        .filter(String::isNotBlank)
        .ifEmpty { listOfNotNull(photoUri) }

    companion object {
        const val PHOTO_SEPARATOR = "|petcare-photo|"
    }
}
