package com.example.petcare.design

import android.content.Context
import android.util.Base64
import androidx.annotation.StringRes
import com.example.petcare.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Result of checking a password against what's stored. */
enum class PasswordMatch {
    /** Correct, and already stored in the current format. */
    MATCH,

    /** Correct, but stored in an old format (e.g. plain text): re-hash and save it now. */
    MATCH_NEEDS_UPGRADE,
    NO_MATCH,
}

/**
 * Password and security-answer hashing for a local (on-device) account store.
 *
 * Passwords are never stored: only a salted PBKDF2 hash, formatted as
 * `pbkdf2:<alg>:<iterations>:<salt>:<hash>`. Each password gets its own random salt,
 * so two users with the same password get different hashes. Hashing is deliberately
 * slow (120,000 iterations) to resist guessing, so every call runs off the main thread.
 */
object AccountSecurity {

    private const val PREFIX = "pbkdf2"
    private const val SEP = ":"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val WHITESPACE = Regex("\\s+")

    private enum class Algorithm(val tag: String, val jca: String) {
        SHA256("sha256", "PBKDF2WithHmacSHA256"),

        // Android 7.x (API 24-25) lacks the SHA-256 variant, so fall back to SHA-1 there.
        SHA1("sha1", "PBKDF2WithHmacSHA1");

        companion object {
            fun fromTag(tag: String): Algorithm? = entries.firstOrNull { it.tag == tag }
        }
    }

    private val preferred: Algorithm by lazy {
        try {
            SecretKeyFactory.getInstance(Algorithm.SHA256.jca)
            Algorithm.SHA256
        } catch (e: NoSuchAlgorithmException) {
            Algorithm.SHA1
        }
    }

    /** Hash a new password for storage. */
    suspend fun hashPassword(password: CharArray): String = withContext(Dispatchers.Default) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt, ITERATIONS, preferred)
        listOf(PREFIX, preferred.tag, ITERATIONS.toString(), encode(salt), encode(hash)).joinToString(SEP)
    }

    /**
     * Check a password against the stored value. Also accepts a legacy plain-text value
     * (from before this update) and reports MATCH_NEEDS_UPGRADE so you can re-hash it.
     */
    suspend fun checkPassword(password: CharArray, stored: String): PasswordMatch = withContext(Dispatchers.Default) {
        if (!isHashed(stored)) {
            val typed = String(password).toByteArray(Charsets.UTF_8)
            val legacy = stored.toByteArray(Charsets.UTF_8)
            return@withContext if (MessageDigest.isEqual(typed, legacy)) PasswordMatch.MATCH_NEEDS_UPGRADE else PasswordMatch.NO_MATCH
        }
        val parts = stored.split(SEP)
        if (parts.size != 5) return@withContext PasswordMatch.NO_MATCH
        val algorithm = Algorithm.fromTag(parts[1]) ?: return@withContext PasswordMatch.NO_MATCH
        val iterations = parts[2].toIntOrNull() ?: return@withContext PasswordMatch.NO_MATCH
        val actual = derive(password, decode(parts[3]), iterations, algorithm)
        // Constant-time comparison, so response time doesn't leak how close a guess was.
        when {
            !MessageDigest.isEqual(actual, decode(parts[4])) -> PasswordMatch.NO_MATCH
            algorithm != preferred || iterations < ITERATIONS -> PasswordMatch.MATCH_NEEDS_UPGRADE
            else -> PasswordMatch.MATCH
        }
    }

    /** Hash a security answer. Case, extra spaces and surrounding spaces are ignored. */
    suspend fun hashAnswer(answer: String): String = hashPassword(normalizeAnswer(answer).toCharArray())

    suspend fun checkAnswer(answer: String, stored: String): Boolean =
        checkPassword(normalizeAnswer(answer).toCharArray(), stored) != PasswordMatch.NO_MATCH

    fun normalizeAnswer(answer: String): String =
        answer.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

    fun isHashed(stored: String): Boolean = stored.startsWith(PREFIX + SEP)

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int, algorithm: Algorithm): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm.jca).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}

/** The rules shown in the password checklist. */
enum class PasswordRule(@StringRes val label: Int) {
    LENGTH(R.string.pc_rule_length),
    CASE(R.string.pc_rule_case),
    NUMBER(R.string.pc_rule_number),
    SYMBOL(R.string.pc_rule_symbol),
    NOT_COMMON(R.string.pc_rule_personal),
}

/** [score] runs 0 (weak) to 4 (strong). */
data class PasswordCheck(val score: Int, val met: Set<PasswordRule>) {
    /** Symbols raise the score but aren't required. */
    val acceptable: Boolean get() = PasswordPolicy.REQUIRED.all { it in met }
}

object PasswordPolicy {

    val REQUIRED = setOf(PasswordRule.LENGTH, PasswordRule.CASE, PasswordRule.NUMBER, PasswordRule.NOT_COMMON)

    private val COMMON = setOf(
        "password", "password1", "password123", "passw0rd", "123456", "1234567", "12345678",
        "123456789", "1234567890", "12345", "111111", "000000", "123123", "654321", "qwerty",
        "qwerty123", "qwertyuiop", "asdfghjkl", "abc123", "abcd1234", "iloveyou", "admin",
        "admin123", "welcome", "welcome1", "letmein", "monkey", "dragon", "football", "baseball",
        "sunshine", "princess", "shadow", "superman", "michael", "trustno1", "petcare", "petcare123",
        "mydog123", "mycat123", "puppy123", "kitty123", "doggy123", "nepal123", "kathmandu",
    )

    /** [personal] = the user's name, email, and so on: passwords containing them fail NOT_COMMON. */
    fun check(password: String, personal: List<String?> = emptyList()): PasswordCheck {
        if (password.isEmpty()) return PasswordCheck(0, emptySet())
        val met = mutableSetOf<PasswordRule>()
        if (password.length >= 8) met += PasswordRule.LENGTH
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) met += PasswordRule.CASE
        if (password.any { it.isDigit() }) met += PasswordRule.NUMBER
        if (password.any { !it.isLetterOrDigit() && !it.isWhitespace() }) met += PasswordRule.SYMBOL

        val lower = password.lowercase(Locale.ROOT)
        val tokens = personal.filterNotNull()
            .flatMap { it.substringBefore('@').lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")) }
            .filter { it.length >= 3 }
        if (lower !in COMMON && tokens.none { lower.contains(it) }) met += PasswordRule.NOT_COMMON

        var score = listOf(PasswordRule.LENGTH, PasswordRule.CASE, PasswordRule.NUMBER, PasswordRule.SYMBOL)
            .count { it in met }
        if (password.length >= 12) score += 1
        if (PasswordRule.NOT_COMMON !in met || PasswordRule.LENGTH !in met) score = minOf(score, 1)
        return PasswordCheck(score.coerceIn(0, 4), met)
    }
}

/**
 * Slows down password guessing. After 5 wrong attempts the account locks for 30 seconds,
 * then doubles with each further failure, up to 15 minutes. A success clears it.
 * Use one instance per account (e.g. the email), or "reset:<email>" for security answers.
 */
class LoginThrottle(context: Context, account: String) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val key = account.trim().lowercase(Locale.ROOT)

    val failures: Int get() = prefs.getInt("f_$key", 0)

    fun remainingLockMillis(now: Long = System.currentTimeMillis()): Long =
        (prefs.getLong("u_$key", 0L) - now).coerceAtLeast(0L)

    /** Attempts left before the next lock. */
    fun attemptsBeforeLock(): Int = (FREE_ATTEMPTS - failures).coerceAtLeast(0)

    /** Records a failure and returns the lock length it triggered (0 if none). */
    fun recordFailure(now: Long = System.currentTimeMillis()): Long {
        val count = failures + 1
        val lockMs = if (count >= FREE_ATTEMPTS) {
            val doublings = (count - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
            (BASE_LOCK_MS shl doublings).coerceAtMost(MAX_LOCK_MS)
        } else {
            0L
        }
        prefs.edit()
            .putInt("f_$key", count)
            .putLong("u_$key", if (lockMs > 0L) now + lockMs else 0L)
            .apply()
        return lockMs
    }

    fun recordSuccess() {
        prefs.edit().remove("f_$key").remove("u_$key").apply()
    }

    private companion object {
        const val PREFS = "pc_throttle"
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_LOCK_MS = 15 * 60_000L
        const val MAX_DOUBLINGS = 5
    }
}
