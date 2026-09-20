package com.example.petcare.data.local.expense

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.petcare.data.local.pet.PetEntity

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("petId")]
)
/** Expense amount in integer cents with its owner and pet relationship. */
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ownerId: Long = 1,
    val petId: Long,
    val category: String,
    val amountCents: Long,
    val dateEpochDay: Long,
    val note: String = ""
)
