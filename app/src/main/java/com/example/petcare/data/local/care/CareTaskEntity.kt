package com.example.petcare.data.local.care

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.petcare.data.local.pet.PetEntity

@Entity(
    tableName = "care_tasks",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["petId"])]
)
/** A scheduled care action, including recurrence and optional saved-place coordinates. */
data class CareTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ownerId: Long = 1,
    val petId: Long,
    val title: String,
    val dueDateEpochDay: Long,
    val reminderMinutesOfDay: Int = DEFAULT_REMINDER_MINUTES_OF_DAY,
    val category: String = CARE_CATEGORY_GENERAL,
    val frequency: String = CARE_FREQUENCY_ONE_TIME,
    val requiredSupplies: String = "",
    val notes: String = "",
    val isCompleted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeId: Long? = null,
    val sortOrder: Long = 0,
    val generatedFromId: Long? = null
)

const val DEFAULT_REMINDER_MINUTES_OF_DAY = 9 * 60
const val CARE_CATEGORY_GENERAL = "General"
const val CARE_FREQUENCY_ONE_TIME = "One time"
const val CARE_FREQUENCY_DAILY = "Daily"
const val CARE_FREQUENCY_WEEKLY = "Weekly"
const val CARE_FREQUENCY_MONTHLY = "Monthly"
