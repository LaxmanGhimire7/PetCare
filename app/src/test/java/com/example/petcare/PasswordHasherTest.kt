package com.example.petcare

import com.example.petcare.data.local.user.PasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Account passwords must be salted and verifiable on both supported PBKDF2 variants. */
class PasswordHasherTest {
    @Test fun randomSaltCreatesDistinctHashesAndRejectsWrongPassword() {
        val first = PasswordHasher.create("correct horse battery staple")
        val second = PasswordHasher.create("correct horse battery staple")
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.encoded, second.encoded)
        assertTrue(PasswordHasher.verify("correct horse battery staple", first.encoded,
            first.salt, first.algorithm))
        assertFalse(PasswordHasher.verify("wrong", first.encoded, first.salt, first.algorithm))
    }

    @Test fun api24FallbackStillVerifies() {
        val hash = PasswordHasher.create("password123", PasswordHasher.SHA1)
        assertTrue(PasswordHasher.verify("password123", hash.encoded, hash.salt,
            hash.algorithm))
    }
}
