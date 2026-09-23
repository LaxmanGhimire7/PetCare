package com.example.petcare.data.local.user

import android.content.Context
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.BiometricPreferences
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.design.AccountSecurity
import com.example.petcare.design.PasswordMatch
import com.example.petcare.ui.onboarding.OnboardingPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

/** Account storage used by the v3 authentication and recovery screens. */
class AuthRepository(private val context: Context) {
    private val prefs = AuthPreferences(context)
    private val users = PetCareDatabase.getInstance(context).userDao()

    suspend fun bootstrap(): Long = withContext(Dispatchers.IO) {
        migrateLegacy()
        val id = prefs.ownerId()
        if (id > 0 && users.getById(id) == null) prefs.signOut()
        if (prefs.isSignedIn()) prefs.ownerId() else 0L
    }

    suspend fun register(
        name: String,
        email: String,
        phone: String?,
        password: CharArray,
        securityQuestion: String,
        securityAnswer: String,
    ): Boolean = withContext(Dispatchers.IO) {
        migrateLegacy()
        val normalized = email.trim().lowercase(Locale.ROOT)
        if (users.getByEmail(normalized) != null) return@withContext false
        val passwordHash = AccountSecurity.hashPassword(password)
        val answerHash = AccountSecurity.hashAnswer(securityAnswer)
        val id = runCatching {
            users.insert(
                UserEntity(
                    name = name.trim(),
                    email = normalized,
                    passwordHash = passwordHash,
                    passwordSalt = "",
                    hashAlgorithm = PBKDF2,
                    phone = phone?.trim()?.takeIf(String::isNotEmpty),
                    securityQuestion = securityQuestion,
                    securityAnswerHash = answerHash,
                ),
            )
        }.getOrElse { return@withContext false }
        prefs.setSession(id, name.trim(), true)
        OnboardingPrefs(context).markComplete(id)
        true
    }

    suspend fun signIn(email: String, password: CharArray, staySignedIn: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            migrateLegacy()
            val user = users.getByEmail(email.trim().lowercase(Locale.ROOT)) ?: return@withContext false
            val match = passwordMatch(password, user)
            if (!match.first) return@withContext false
            if (match.second) {
                users.upgradePassword(user.id, AccountSecurity.hashPassword(password), "", PBKDF2)
            }
            prefs.setSession(user.id, user.name, staySignedIn)
            true
        }

    suspend fun currentUser(): UserEntity? = withContext(Dispatchers.IO) {
        users.getById(prefs.ownerId())
    }

    suspend fun securityQuestionFor(email: String): String? = withContext(Dispatchers.IO) {
        users.getByEmail(email.trim().lowercase(Locale.ROOT))?.securityQuestion
    }

    suspend fun checkSecurityAnswer(email: String, answer: String): Boolean = withContext(Dispatchers.IO) {
        val stored = users.getByEmail(email.trim().lowercase(Locale.ROOT))?.securityAnswerHash
            ?: return@withContext false
        AccountSecurity.checkAnswer(answer, stored)
    }

    suspend fun resetPassword(email: String, newPassword: CharArray) = withContext(Dispatchers.IO) {
        val user = users.getByEmail(email.trim().lowercase(Locale.ROOT)) ?: return@withContext
        users.upgradePassword(user.id, AccountSecurity.hashPassword(newPassword), "", PBKDF2)
    }

    suspend fun saveRecovery(question: String, answer: String): Boolean = withContext(Dispatchers.IO) {
        val id = prefs.ownerId()
        if (id <= 0 || users.getById(id) == null) return@withContext false
        users.updateRecovery(id, question, AccountSecurity.hashAnswer(answer))
        true
    }

    /** Called only after AndroidX BiometricPrompt reports successful device authentication. */
    suspend fun unlockWithBiometric(userId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!BiometricPreferences(context).isEnabledFor(userId)) return@withContext false
        val user = users.getById(userId) ?: return@withContext false
        prefs.setSession(user.id, user.name, prefs.staySignedIn())
        true
    }

    /** Returns valid + whether the verifier must be upgraded to the v3 format. */
    private suspend fun passwordMatch(password: CharArray, user: UserEntity): Pair<Boolean, Boolean> {
        if (AccountSecurity.isHashed(user.passwordHash)) {
            return when (AccountSecurity.checkPassword(password, user.passwordHash)) {
                PasswordMatch.MATCH -> true to false
                PasswordMatch.MATCH_NEEDS_UPGRADE -> true to true
                PasswordMatch.NO_MATCH -> false to false
            }
        }
        if (user.passwordSalt.isNotBlank() &&
            user.hashAlgorithm in setOf(PasswordHasher.SHA1, PasswordHasher.SHA256)
        ) {
            val valid = runCatching {
                PasswordHasher.verify(String(password), user.passwordHash, user.passwordSalt, user.hashAlgorithm)
            }.getOrDefault(false)
            return valid to valid
        }
        if (user.hashAlgorithm == LEGACY_SHA256) {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(String(password).toByteArray())
                .joinToString("") { "%02x".format(it) }
            val valid = MessageDigest.isEqual(digest.toByteArray(), user.passwordHash.toByteArray())
            return valid to valid
        }
        return when (AccountSecurity.checkPassword(password, user.passwordHash)) {
            PasswordMatch.MATCH, PasswordMatch.MATCH_NEEDS_UPGRADE -> true to true
            PasswordMatch.NO_MATCH -> false to false
        }
    }

    /** Existing rows were assigned owner 1 by migration 11->12, so retain that id. */
    private suspend fun migrateLegacy() {
        val email = prefs.legacyEmail()?.trim()?.lowercase(Locale.ROOT) ?: return
        val hash = prefs.legacyHash() ?: return
        val existing = users.getByEmail(email)
        val id = existing?.id ?: users.insert(
            UserEntity(
                id = 1,
                name = prefs.legacyName().orEmpty(),
                email = email,
                passwordHash = hash,
                passwordSalt = "",
                hashAlgorithm = LEGACY_SHA256,
            ),
        )
        if (prefs.legacyWasSignedIn() && id == 1L) prefs.setSession(1, prefs.legacyName().orEmpty(), true)
        if (id == 1L) OnboardingPrefs(context).markComplete(1)
        prefs.clearLegacyCredentials()
    }

    private companion object {
        const val LEGACY_SHA256 = "SHA256_LEGACY"
        const val PBKDF2 = "PBKDF2"
    }
}
