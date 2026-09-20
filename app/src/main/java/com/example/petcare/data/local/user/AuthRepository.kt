package com.example.petcare.data.local.user

import android.content.Context
import android.os.Build
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.PetCareDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import com.example.petcare.ui.onboarding.OnboardingPrefs

/** Creates accounts and moves the original single-account login to the user table. */
class AuthRepository(private val context: Context) {
    private val prefs = AuthPreferences(context)
    private val users = PetCareDatabase.getInstance(context).userDao()

    suspend fun bootstrap(): Long = withContext(Dispatchers.IO) {
        migrateLegacy()
        val id = prefs.ownerId()
        if (id > 0 && users.getById(id) == null) prefs.signOut()
        if (prefs.isSignedIn()) prefs.ownerId() else 0L
    }

    suspend fun register(name: String, email: String, password: String, staySignedIn: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            migrateLegacy()
            val normalized = email.trim().lowercase()
            if (users.getByEmail(normalized) != null) return@withContext false
            val hash = PasswordHasher.create(password, supportedAlgorithm())
            val id = runCatching { users.insert(UserEntity(name = name.trim(), email = normalized,
                passwordHash = hash.encoded, passwordSalt = hash.salt,
                hashAlgorithm = hash.algorithm)) }.getOrElse { return@withContext false }
            prefs.setSession(id, name.trim(), staySignedIn)
            true
        }

    suspend fun signIn(email: String, password: String, staySignedIn: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            migrateLegacy()
            val user = users.getByEmail(email.trim().lowercase()) ?: return@withContext false
            val valid = if (user.hashAlgorithm == LEGACY_SHA256) {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
                MessageDigest.isEqual(digest.toByteArray(), user.passwordHash.toByteArray())
            } else runCatching { PasswordHasher.verify(password, user.passwordHash,
                user.passwordSalt, user.hashAlgorithm) }.getOrDefault(false)
            if (!valid) return@withContext false
            if (user.hashAlgorithm == LEGACY_SHA256) {
                val hash = PasswordHasher.create(password, supportedAlgorithm())
                users.upgradePassword(user.id, hash.encoded, hash.salt, hash.algorithm)
            }
            prefs.setSession(user.id, user.name, staySignedIn)
            true
        }

    /** Called only after AndroidX BiometricPrompt reports successful device authentication. */
    suspend fun unlockWithBiometric(userId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!com.example.petcare.data.local.BiometricPreferences(context).isEnabledFor(userId))
            return@withContext false
        val user = users.getById(userId) ?: return@withContext false
        prefs.setSession(user.id, user.name, prefs.staySignedIn())
        true
    }

    /** Existing rows were assigned owner 1 by migration 11→12, so retain that id. */
    private suspend fun migrateLegacy() {
        val email = prefs.legacyEmail()?.trim()?.lowercase() ?: return
        val hash = prefs.legacyHash() ?: return
        val existing = users.getByEmail(email)
        val id = existing?.id ?: users.insert(UserEntity(id = 1,
                name = prefs.legacyName().orEmpty(), email = email,
                passwordHash = hash, passwordSalt = "", hashAlgorithm = LEGACY_SHA256))
        if (prefs.legacyWasSignedIn() && id == 1L)
            prefs.setSession(1, prefs.legacyName().orEmpty(), true)
        if (id == 1L) OnboardingPrefs(context).markComplete(1)
        prefs.clearLegacyCredentials()
    }

    private fun supportedAlgorithm(): String = if (Build.VERSION.SDK_INT >= 26)
        PasswordHasher.SHA256 else PasswordHasher.SHA1

    private companion object { const val LEGACY_SHA256 = "SHA256_LEGACY" }
}
