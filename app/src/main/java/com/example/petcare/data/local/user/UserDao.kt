package com.example.petcare.data.local.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Account lookups are confined to normalized email or the active session id. */
@Dao
/** Room access to local account identities and password verifiers. */
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UserEntity?

    @Query("UPDATE users SET passwordHash = :hash, passwordSalt = :salt, hashAlgorithm = :algorithm WHERE id = :id")
    suspend fun upgradePassword(id: Long, hash: String, salt: String, algorithm: String)
}
