package com.dscorp.wispadmin.wispadmin.dto

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppMessageCandidateDtoJsonTest {

    private val mapper = ObjectMapper()

    @Test
    fun jsonPayloadUsesIsBimonthlyKeyNotBimonthly() {
        val json = mapper.readTree(
            mapper.writeValueAsString(
                WhatsAppMessageCandidateDto(
                    targetType = "PAYMENT",
                    targetId = 101,
                    paymentId = 101,
                    subscriptionId = 633,
                    clientName = "Victoria Isabel",
                    phone = "973715721",
                    amount = 160.0,
                    billingDate = "01/07/2026",
                    paymentDate = null,
                    installationDate = null,
                    alreadySentToday = false,
                    invoiceCount = 2,
                    periodSummary = "01/07/2026 - 01/08/2026",
                    isBimonthly = true,
                )
            )
        )

        assertTrue(json.has("isBimonthly"), "API must expose isBimonthly for the WhatsApp backoffice")
        assertEquals(true, json.get("isBimonthly").booleanValue())
        assertFalse(json.has("bimonthly"), "Jackson must not strip the is prefix from isBimonthly")
    }
}
