package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WhatsAppBotTextSelectionResolverTest {

    private val options = listOf(
        WhatsAppInteractiveOption("internet", "Sin Internet"),
        WhatsAppInteractiveOption("slow", "Internet lento"),
        WhatsAppInteractiveOption("advisor", "Hablar con asesor")
    )

    @Test
    fun `resolves numeric and letter selections`() {
        assertEquals("internet", WhatsAppBotTextSelectionResolver.resolve("1", options))
        assertEquals("slow", WhatsAppBotTextSelectionResolver.resolve("B", options))
    }

    @Test
    fun `resolves normalized option labels`() {
        assertEquals("internet", WhatsAppBotTextSelectionResolver.resolve("sin internet", options))
        assertEquals("slow", WhatsAppBotTextSelectionResolver.resolve("Mi internet está lento", options))
    }

    @Test
    fun `does not guess unrelated text`() {
        assertNull(WhatsAppBotTextSelectionResolver.resolve("quiero información", options))
    }
}
