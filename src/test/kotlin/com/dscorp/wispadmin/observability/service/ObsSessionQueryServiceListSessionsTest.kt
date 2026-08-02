package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsReplayRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.time.ZoneId

class ObsSessionQueryServiceListSessionsTest {

    private val eventRepository = mockk<ObsEventRepository>()
    private val spanRepository = mockk<ObsSpanRepository>()
    private val replayRepository = mockk<ObsReplayRepository>()
    private val objectMapper = ObjectMapper()
    private val zone = ZoneId.systemDefault()

    private val service = ObsSessionQueryService(
        eventRepository,
        spanRepository,
        replayRepository,
        objectMapper,
        zone
    )

    @Test
    fun `listSessions incluye sesion solo con trazas sin eventos`() {
        val from = LocalDateTime.of(2026, 8, 1, 0, 0, 0)
        val to = LocalDateTime.of(2026, 8, 1, 23, 59, 59)
        val fromMs = from.atZone(zone).toInstant().toEpochMilli()
        val toMs = to.atZone(zone).toInstant().toEpochMilli()
        val lastMs = LocalDateTime.of(2026, 8, 1, 12, 0, 0).atZone(zone).toInstant().toEpochMilli()
        val firstMs = LocalDateTime.of(2026, 8, 1, 11, 0, 0).atZone(zone).toInstant().toEpochMilli()

        every {
            eventRepository.aggregateRecentSessions(from, to, null, any<Pageable>())
        } returns PageImpl(emptyList())

        every {
            spanRepository.aggregateRecentSessions(fromMs, toMs, null)
        } returns listOf(
            arrayOf("span-sess-1", "android", 5L, lastMs, firstMs)
        )

        every {
            spanRepository.countRootSpansBySession(listOf("span-sess-1"))
        } returns listOf(arrayOf("span-sess-1", 3L))

        every { replayRepository.findSessionIdsWithReplay(listOf("span-sess-1")) } returns emptyList()
        every { eventRepository.findFirstBySessionIdOrderByCreatedAtDesc("span-sess-1") } returns null
        every { eventRepository.findBySessionIdOrderByCreatedAtDesc("span-sess-1") } returns emptyList()

        val page = service.listSessions(from, to, null, 0, 25)

        assertEquals(1, page.totalElements)
        assertEquals("span-sess-1", page.content.single().sessionId)
        assertEquals("android", page.content.single().platform)
        assertEquals(0L, page.content.single().eventCount)
        assertEquals(3L, page.content.single().traceCount)
    }
}
