package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProvisioningOutboxTest {
    @Test fun `delivery is acknowledged only after receiver confirmation and retains correlation`() {
        val journal = mockk<ProvisioningJournal>(relaxed = true)
        val json = jacksonObjectMapper().findAndRegisterModules()
        val operation = ProvisioningOperation("op", "staging", 42, "HWTC9F4BF950")
        every { journal.events() } returns listOf(ProvisioningEvent(17, operation))
        val payloads = mutableListOf<Pair<String, String>>()
        var accepted = false
        val sender = ProvisioningOutbox(journal, json) { id, payload -> payloads += id to payload; accepted }
        sender.flush()
        verify(exactly = 0) { journal.delivered(any()) }
        accepted = true
        sender.flush()
        verify(exactly = 1) { journal.delivered(17) }
        assertEquals(payloads[0], payloads[1])
        val event = json.readTree(payloads[0].second).path("events")[0]
        assertEquals("op", event.path("correlationId").asText())
        assertEquals(42, event.path("tags").path("subscriptionId").asInt())
        assertEquals("staging", event.path("environment").asText())
    }
}
