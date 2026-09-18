package com.example.petcare.data.local.care

import androidx.room.Entity
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
data class CareTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val title: String,
    val dueDateEpochDay: Long,
    val reminderMinutesOfDay: Int = DEFAULT_REMINDER_MINUTES_OF_DAY,
    val isCompleted: Boolean = false
)

const val DEFAULT_REMINDER_MINUTES_OF_DAY = 9 * 60
