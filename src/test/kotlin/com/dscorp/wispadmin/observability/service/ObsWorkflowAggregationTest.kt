package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.SessionWorkflowSummaryDto
import com.dscorp.wispadmin.observability.entity.ObsEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ObsWorkflowAggregationTest {

    @Test
    fun `agrega workflows por id y toma status del cierre`() {
        val t0 = LocalDateTime.of(2026, 7, 14, 10, 0)
        val events = listOf(
            ObsEvent(workflowId = "wf-1", workflowName = "login", workflowCategory = "auth", eventType = "workflow_start", createdAt = t0),
            ObsEvent(workflowId = "wf-1", workflowName = "login", workflowCategory = "auth", eventType = "log", createdAt = t0.plusMinutes(1)),
            ObsEvent(workflowId = "wf-1", workflowName = "login", workflowCategory = "auth", workflowStatus = "success", eventType = "workflow_end", createdAt = t0.plusMinutes(2)),
            ObsEvent(workflowId = "wf-2", workflowName = "payment", workflowCategory = "billing", eventType = "workflow_start", createdAt = t0.plusMinutes(3))
        )

        val workflows = ObsWorkflowAggregation.fromEvents(events)

        assertEquals(2, workflows.size)
        val login = workflows.first { it.workflowId == "wf-1" }
        assertEquals("login", login.name)
        assertEquals("auth", login.category)
        assertEquals("success", login.status)
        assertEquals(3L, login.eventCount)
        assertEquals(t0, login.firstSeen)
        assertEquals(t0.plusMinutes(2), login.lastSeen)

        val payment = workflows.first { it.workflowId == "wf-2" }
        assertEquals("interrupted", payment.status)
        assertEquals(1L, payment.eventCount)
    }
}
