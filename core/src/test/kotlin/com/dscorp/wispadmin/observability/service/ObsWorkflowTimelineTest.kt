package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.entity.ObsEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ObsWorkflowTimelineTest {

    @Test
    fun `joins android and backend events of one operation in chronological order without pii`() {
        val t0 = LocalDateTime.of(2026, 10, 1, 12, 0)
        val events = listOf(
            ObsEvent(platform = "backend", eventType = "log", severity = "info", message = "Alta FIBER: PENDING",
                correlationId = "op-1", workflowId = "op-1", workflowStatus = "running", createdAt = t0.plusSeconds(2),
                tagsJson = """{"serial":"ZTEG1","subscriptionId":90}"""),
            ObsEvent(platform = "android", eventType = "workflow_start", severity = "info", message = "registro_suscripcion",
                workflowId = "op-1", createdAt = t0, userJson = """{"username":"tec"}"""),
            ObsEvent(platform = "android", eventType = "http_error", severity = "warning", message = "HTTP 504",
                workflowId = "op-1", url = "https://api/ispadmin/subscription?dni=12345678", httpStatus = 504,
                createdAt = t0.plusSeconds(1)),
        )

        val timeline = ObsWorkflowTimeline.from(events)

        assertEquals(listOf("android", "android", "backend"), timeline.map { it.platform })
        assertEquals("workflow_start", timeline[0].eventType)
        assertEquals("https://api/ispadmin/subscription", timeline[1].url)
        assertEquals(504, timeline[1].httpStatus)
        assertEquals("running", timeline[2].workflowStatus)
    }
}
