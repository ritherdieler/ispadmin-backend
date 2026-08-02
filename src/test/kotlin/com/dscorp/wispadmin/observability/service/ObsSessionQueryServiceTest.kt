package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.entity.ObsEvent
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsReplayRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.time.ZoneId

class ObsSessionQueryServiceTest {

    private val eventRepository = mockk<ObsEventRepository>()
    private val spanRepository = mockk<ObsSpanRepository>()
    private val replayRepository = mockk<ObsReplayRepository>()
    private val objectMapper = ObjectMapper()

    private val service = ObsSessionQueryService(
        eventRepository,
        spanRepository,
        replayRepository,
        objectMapper,
        ZoneId.systemDefault()
    )

    @Test
    fun `getSession usa el usuario del evento mas reciente que lo tenga aunque el primero no lo tenga`() {
        val ts = LocalDateTime.of(2026, 7, 14, 10, 51, 10)
        val withoutUser = ObsEvent(
            id = 81,
            eventType = "workflow_start",
            message = "login",
            platform = "android",
            sessionId = "sess-1",
            userJson = null,
            createdAt = ts
        )
        val withUser = ObsEvent(
            id = 82,
            eventType = "workflow_end",
            message = "success",
            platform = "android",
            sessionId = "sess-1",
            userJson = """{"id":1,"username":"dscorp"}""",
            createdAt = ts
        )

        every { eventRepository.findBySessionIdOrderByCreatedAtDesc("sess-1") } returns listOf(withoutUser, withUser)
        every {
            spanRepository.searchRootSpans(any(), any(), any(), any(), any(), any(), eq("sess-1"), any(), any<Pageable>())
        } returns PageImpl(emptyList())
        every { replayRepository.findBySessionIdOrderByCreatedAtAsc("sess-1") } returns emptyList()

        val detail = service.getSession("sess-1")

        assertNotNull(detail)
        val user = detail!!.summary.user as Map<*, *>
        assertEquals("dscorp", user["username"])
    }

    @Test
    fun `getSession deja user null si ningun evento tiene usuario`() {
        every { eventRepository.findBySessionIdOrderByCreatedAtDesc("sess-2") } returns listOf(
            ObsEvent(id = 1, sessionId = "sess-2", platform = "android", userJson = null, createdAt = LocalDateTime.now()),
            ObsEvent(id = 2, sessionId = "sess-2", platform = "android", userJson = "  ", createdAt = LocalDateTime.now())
        )
        every {
            spanRepository.searchRootSpans(any(), any(), any(), any(), any(), any(), eq("sess-2"), any(), any<Pageable>())
        } returns PageImpl(emptyList())
        every { replayRepository.findBySessionIdOrderByCreatedAtAsc("sess-2") } returns emptyList()

        val detail = service.getSession("sess-2")

        assertNotNull(detail)
        assertNull(detail!!.summary.user)
    }
}
