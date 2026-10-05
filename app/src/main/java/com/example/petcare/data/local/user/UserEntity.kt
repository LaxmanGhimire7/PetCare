package com.example.petcare.data.local.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A local account. Salt and hash are encoded separately; no password is persisted. */
@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
/** One local account, with a salted password verifier instead of plaintext. */
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val hashAlgorithm: String = PasswordHasher.SHA256,
    val phone: String? = null,
    val securityQuestion: String? = null,
    val securityAnswerHash: String? = null,
    @ColumnInfo(defaultValue = "'password'") val authProvider: String = "password",
)
