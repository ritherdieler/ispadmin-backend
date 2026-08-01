package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagOltLogEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagOltLogEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Instant

class NetDiagOltLogQueryServiceTest {

    private val repository = mockk<NetDiagOltLogEventRepository>()
    private val service = NetDiagOltLogQueryService(repository)

    @Test
    fun `lista logs mapea DTO y pagina`() {
        val pageableSlot = slot<Pageable>()
        val event = NetDiagOltLogEvent(
            id = 7L,
            receivedAt = Instant.parse("2026-07-31T17:00:00Z"),
            sourceIp = "10.11.104.2",
            rawMessage = "raw",
            reasonCode = "OLT_ALARM_UNPARSED",
            board = 1,
            port = 8,
            isUnparsed = true,
            channel = "cli_alarm_active"
        )
        every {
            repository.search(1, 8, true, null, null, capture(pageableSlot))
        } returns PageImpl(listOf(event), org.springframework.data.domain.PageRequest.of(0, 20), 1)

        val page = service.listLogs(
            board = 1,
            port = 8,
            unparsedOnly = true,
            dateFrom = null,
            dateTo = null,
            page = 0,
            size = 20
        )

        assertEquals(1, page.totalElements)
        assertEquals(1, page.items.size)
        assertEquals("OLT_ALARM_UNPARSED", page.items[0].reasonCode)
        assertTrue(page.items[0].isUnparsed)
        assertEquals("raw", page.items[0].rawMessage)
        assertEquals(0, pageableSlot.captured.pageNumber)
        assertEquals(20, pageableSlot.captured.pageSize)
    }
}
