package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppTicketDescriptionFormatterTest {

    @Test
    fun `Test 1 NO_INTERNET with fiber_red produces human readable description`() {
        val description = WhatsAppTicketDescriptionFormatter.buildHumanReadableTicketDescription(
            issueCode = "NO_INTERNET",
            buttonReplyId = "support_diag_fiber_red",
        )

        assertTrue(description.contains("Reportado desde WhatsApp:"))
        assertTrue(description.contains("sin servicio de internet"), description)
        assertTrue(description.contains("Luz roja en el módem/ONT"), description)
        assertTrue(description.contains("Origen: Automatización Bot de WhatsApp"))
        assertFalse(description.contains("NO_INTERNET"))
        assertFalse(description.contains("diagnostico=red"))
        assertFalse(description.contains("null"))
    }

    @Test
    fun `Test 2 null or unmapped values use readable fallbacks without null`() {
        val description = WhatsAppTicketDescriptionFormatter.buildHumanReadableTicketDescription(
            issueCode = null,
            buttonReplyId = null,
        )

        assertTrue(description.contains("problema técnico con su servicio"), description)
        assertTrue(description.contains("Diagnóstico inicial registrado por el bot."), description)
        assertTrue(description.contains("Origen: Automatización Bot de WhatsApp"))
        assertFalse(description.contains("null"))
        assertFalse(description.contains("diagnostico="))
    }

    @Test
    fun `SLOW_INTERNET and green light are humanized`() {
        val description = WhatsAppTicketDescriptionFormatter.buildHumanReadableTicketDescription(
            issueCode = "SLOW_INTERNET",
            buttonReplyId = "support_diag_fiber_green",
        )

        assertTrue(description.contains("lentitud en la velocidad de navegación"), description)
        assertTrue(description.contains("Luces normales/verdes"), description)
    }

    @Test
    fun `INTERNET_INTERRUPTION maps to intermittent phrasing`() {
        val description = WhatsAppTicketDescriptionFormatter.buildHumanReadableTicketDescription(
            issueCode = "INTERNET_INTERRUPTION",
            buttonReplyId = "support_diag_unknown_xyz",
        )

        assertTrue(description.contains("intermitencia o caídas constantes"), description)
        assertTrue(description.contains("Diagnóstico inicial registrado por el bot."), description)
    }
}
