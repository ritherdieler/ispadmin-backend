package com.dscorp.wispadmin.wispadmin.service.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PppoeCredentialFactoryTest {

    @Test
    fun `username follows the canonical gf id form`() {
        assertEquals("gf2338", PppoeCredentialFactory.username(2338))
    }

    @Test
    fun `password is long enough to be safe`() {
        val password = PppoeCredentialFactory.password()

        assertTrue(password.length >= 16, "too short: ${password.length}")
    }

    @Test
    fun `two passwords are never the same`() {
        val passwords = (1..200).map { PppoeCredentialFactory.password() }.toSet()

        assertEquals(200, passwords.size)
    }

    @Test
    fun `password avoids characters that break routeros or confuse support`() {
        repeat(200) {
            val password = PppoeCredentialFactory.password()
            assertTrue(
                password.all { it in PppoeCredentialFactory.ALPHABET },
                "unexpected character in $password"
            )
        }
    }

    @Test
    fun `password has no ambiguous glyphs`() {
        listOf('O', '0', 'l', 'I', '1').forEach { char ->
            assertTrue(char !in PppoeCredentialFactory.ALPHABET, "ambiguous char $char")
        }
    }

    @Test
    fun `consecutive usernames differ`() {
        assertNotEquals(PppoeCredentialFactory.username(1), PppoeCredentialFactory.username(2))
    }
}
