package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeruvianWhatsAppPhoneTest {

    @Test
    fun `toInternational adds 51 prefix for nine digit mobile`() {
        assertEquals("51932489604", PeruvianWhatsAppPhone.toInternational("932489604"))
        assertEquals("51932489604", PeruvianWhatsAppPhone.toInternational("+51 932 489 604"))
    }

    @Test
    fun `toInternational keeps eleven digit mobile`() {
        assertEquals("51932489604", PeruvianWhatsAppPhone.toInternational("51932489604"))
    }

    @Test
    fun `toInternational rejects invalid numbers`() {
        assertThrows(IllegalArgumentException::class.java) {
            PeruvianWhatsAppPhone.toInternational("12345")
        }
    }

    @Test
    fun `queryVariants returns local and international forms`() {
        assertEquals(
            listOf("51932489604", "932489604"),
            PeruvianWhatsAppPhone.queryVariants("932489604")
        )
        assertEquals(
            listOf("51932489604", "932489604"),
            PeruvianWhatsAppPhone.queryVariants("51932489604")
        )
    }

    @Test
    fun `equivalent matches nine and eleven digit forms`() {
        assertTrue(PeruvianWhatsAppPhone.equivalent("932489604", "51932489604"))
        assertFalse(PeruvianWhatsAppPhone.equivalent("932489604", "51987654321"))
    }

    @Test
    fun `canonicalStoragePhone persists international when valid`() {
        assertEquals("51932489604", PeruvianWhatsAppPhone.canonicalStoragePhone("932489604"))
    }
}
