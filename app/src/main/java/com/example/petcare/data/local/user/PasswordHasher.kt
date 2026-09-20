package com.example.petcare.data.local.user

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2 password storage with a random per-account salt and constant-time verification. */
object PasswordHasher {
    const val SHA256 = "PBKDF2WithHmacSHA256"
    const val SHA1 = "PBKDF2WithHmacSHA1"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    /** Salt, derived password hash, and algorithm needed for later verification. */
    data class Hash(val encoded: String, val salt: String, val algorithm: String)

    fun create(password: String, algorithm: String = SHA256): Hash {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        return Hash(derive(password, salt, algorithm).toHex(), salt.toHex(), algorithm)
    }

    fun verify(password: String, encoded: String, salt: String, algorithm: String): Boolean {
        val expected = encoded.hexBytes() ?: return false
        val saltBytes = salt.hexBytes() ?: return false
        if (saltBytes.size != SALT_BYTES) return false
        return MessageDigest.isEqual(expected, derive(password, saltBytes, algorithm))
    }

    private fun derive(password: String, salt: ByteArray, algorithm: String): ByteArray {
        require(algorithm == SHA256 || algorithm == SHA1)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try { SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching { chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
    }
}
