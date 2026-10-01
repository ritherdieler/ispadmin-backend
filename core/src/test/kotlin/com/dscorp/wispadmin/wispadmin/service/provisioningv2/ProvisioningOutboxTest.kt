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

    private fun emitted(operation: ProvisioningOperation): com.fasterxml.jackson.databind.JsonNode {
        val journal = mockk<ProvisioningJournal>(relaxed = true)
        val json = jacksonObjectMapper().findAndRegisterModules()
        every { journal.events() } returns listOf(ProvisioningEvent(1, operation))
        var payload = ""
        ProvisioningOutbox(journal, json) { _, body -> payload = body; true }.flush()
        return json.readTree(payload).path("events")[0]
    }

    @Test fun `retryable wait is a warning log with stage and failure code, not an error`() {
        val failure = ProvisioningFailure("STAGE_EXECUTION_FAILED", "Read timed out", true)
        val operation = ProvisioningOperation("op", "staging", 42, "HWTC9F4BF950", state = ProvisioningState.WAITING,
            checkpoints = ProvisioningStage.values().map { stage ->
                when (stage) {
                    ProvisioningStage.VALIDATE -> StageCheckpoint(stage, CheckpointState.SUCCEEDED, attempts = 1)
                    ProvisioningStage.MIKROTIK -> StageCheckpoint(stage, CheckpointState.WAITING, attempts = 2, failure = failure)
                    else -> StageCheckpoint(stage)
                }
            })

        val event = emitted(operation)

        assertEquals("log", event.path("eventType").asText())
        assertEquals("warning", event.path("severity").asText())
        assertEquals("waiting", event.path("tags").path("workflowStatus").asText())
        assertEquals("MIKROTIK", event.path("tags").path("stage").asText())
        assertEquals("STAGE_EXECUTION_FAILED", event.path("tags").path("failureCode").asText())
        assertEquals(2, event.path("tags").path("attempt").asInt())
    }

    @Test fun `terminal failure is reported as an error`() {
        val failure = ProvisioningFailure("ONU_ALREADY_RESERVED", "Reservada", false)
        val operation = ProvisioningOperation("op", "staging", 42, "HWTC9F4BF950", state = ProvisioningState.FAILED,
            checkpoints = ProvisioningStage.values().map { stage ->
                if (stage == ProvisioningStage.VALIDATE) StageCheckpoint(stage, CheckpointState.FAILED, attempts = 1, failure = failure)
                else StageCheckpoint(stage)
            })

        val event = emitted(operation)

        assertEquals("error", event.path("eventType").asText())
        assertEquals("failed", event.path("tags").path("workflowStatus").asText())
        assertEquals("ONU_ALREADY_RESERVED", event.path("tags").path("failureCode").asText())
    }
}
