package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CrmSecretCipherTest {

    private val cipher = CrmSecretCipher("test-master-key-for-crm-secrets-32b")

    @Test
    fun `encrypt and decrypt roundtrip`() {
        val plain = "sk-proj-abcdefghijklmnopqrstuvwxyz"
        val encrypted = cipher.encrypt(plain)
        assertNotEquals(plain, encrypted)
        assertEquals(plain, cipher.decrypt(encrypted))
    }

    @Test
    fun `same plaintext produces different ciphertext`() {
        val plain = "sk-test-key"
        assertNotEquals(cipher.encrypt(plain), cipher.encrypt(plain))
    }

    @Test
    fun `maskApiKey keeps prefix and last 4`() {
        assertEquals("sk-...wxyz", cipher.maskApiKey("sk-abcdefghijkwxyz"))
        assertEquals("****", cipher.maskApiKey("ab"))
        assertEquals(null, cipher.maskApiKey(null))
        assertEquals(null, cipher.maskApiKey(""))
    }

    @Test
    fun `decrypt fails with wrong master key`() {
        val encrypted = cipher.encrypt("secret")
        val other = CrmSecretCipher("another-master-key-value-xxxxxx")
        assertThrows<IllegalStateException> { other.decrypt(encrypted) }
    }

    @Test
    fun `blank master key is rejected`() {
        assertThrows<IllegalArgumentException> { CrmSecretCipher("  ") }
    }

    @Test
    fun `looksEncrypted detects payload format`() {
        val encrypted = cipher.encrypt("value")
        assertTrue(cipher.looksEncrypted(encrypted))
        assertFalse(cipher.looksEncrypted("sk-plain"))
    }
}
