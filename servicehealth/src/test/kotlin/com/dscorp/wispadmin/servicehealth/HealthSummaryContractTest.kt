package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.dto.HealthSummary
import com.dscorp.wispadmin.servicehealth.dto.ServiceContext
import com.dscorp.wispadmin.servicehealth.dto.SubscriberContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Instant

class HealthSummaryContractTest {
    private val mapper = ObjectMapper().findAndRegisterModules()

    @Test
    fun `serializes subscriber and service context additively in snake case`() {
        val summary = baseSummary().copy(
            subscriber = SubscriberContext("Nombre Apellido", "PERSON"),
            serviceContext = ServiceContext("ACTIVE", "Fibra 500 Mbps", "10.0.0.5"),
        )

        val json = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(summary)

        assertEquals("Nombre Apellido", json.at("/subscriber/display_name").asText())
        assertEquals("PERSON", json.at("/subscriber/client_type").asText())
        assertEquals("ACTIVE", json.at("/service_context/service_status").asText())
        assertEquals("Fibra 500 Mbps", json.at("/service_context/plan_name").asText())
        assertEquals("10.0.0.5", json.at("/service_context/ip").asText())
        assertEquals(2329, json.at("/subscription_id").asInt())
    }

    @Test
    fun `keeps the previous constructor contract valid`() {
        val summary = baseSummary()

        assertEquals(2329, summary.subscriptionId)
        assertFalse(summary.pilotEnabled)
        assertEquals(null, summary.subscriber)
        assertEquals(null, summary.serviceContext)
    }

    private fun baseSummary() = HealthSummary(
        subscriptionId = 2329,
        evaluatedAt = Instant.parse("2026-09-01T10:00:00Z"),
        states = emptyMap(),
        sources = emptyList(),
        diagnoses = emptyList(),
        missingEvidence = emptyList(),
        identity = emptyMap(),
        pilotEnabled = false,
        actionsEnabled = false,
    )
}
