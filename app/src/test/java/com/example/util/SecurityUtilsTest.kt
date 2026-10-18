package com.example.util

import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityUtilsTest {
    @Test
    fun `bcrypt hashes use random salt and verify password`() {
        val password = "StudySpace@123"

        val firstHash = SecurityUtils.hashPassword(password)
        val secondHash = SecurityUtils.hashPassword(password)

        assertNotEquals(firstHash, secondHash)
        assertTrue(firstHash.startsWith("\$2"))
        assertTrue(SecurityUtils.verifyPassword(password, firstHash))
        assertFalse(SecurityUtils.verifyPassword("wrong-password", firstHash))
    }

    @Test
    fun `legacy password values are marked for rehash`() {
        val bcryptHash = SecurityUtils.hashPassword("secret")

        assertFalse(SecurityUtils.needsPasswordRehash(bcryptHash))
        assertTrue(SecurityUtils.needsPasswordRehash("plain-text-password"))
        assertTrue(SecurityUtils.verifyPassword("plain-text-password", "plain-text-password"))
        assertFalse(SecurityUtils.verifyPassword("wrong", "plain-text-password"))
    }

    @Test
    fun `legacy local hash verifies once then can be replaced with bcrypt`() {
        val password = "legacy-account-password"
        val legacy = MessageDigest.getInstance("SHA-256")
            .digest((password + "StudySpaceSecureSalt123!").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertTrue(SecurityUtils.verifyPassword(password, legacy))
        assertTrue(SecurityUtils.needsPasswordRehash(legacy))

        val upgraded = SecurityUtils.hashPassword(password)
        assertFalse(SecurityUtils.needsPasswordRehash(upgraded))
        assertTrue(SecurityUtils.verifyPassword(password, upgraded))
        assertFalse(SecurityUtils.verifyPassword("wrong", upgraded))
    }
}
