package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssistanceTicketCountsTest {

    private val repository = mockk<AssistanceTicketRepository>()
    private val service = AssistanceTicketCountService(repository)

    private fun row(status: AssistanceTicketStatus, total: Long): Array<Any> {
        @Suppress("UNCHECKED_CAST")
        return arrayOf(status, total) as Array<Any>
    }

    @Test
    fun `agrupa los conteos por estado con una sola consulta`() {
        every { repository.countGroupedByStatus() } returns listOf(
            row(AssistanceTicketStatus.PENDING, 12),
            row(AssistanceTicketStatus.ASSIGNED, 3),
            row(AssistanceTicketStatus.CLOSED, 40)
        )

        val counts = service.countByStatus()

        verify(exactly = 1) { repository.countGroupedByStatus() }
        assertEquals(12L, counts.byStatus[AssistanceTicketStatus.PENDING.name])
        assertEquals(3L, counts.byStatus[AssistanceTicketStatus.ASSIGNED.name])
        assertEquals(40L, counts.byStatus[AssistanceTicketStatus.CLOSED.name])
    }

    @Test
    fun `los estados sin tickets se devuelven en cero`() {
        every { repository.countGroupedByStatus() } returns listOf(
            row(AssistanceTicketStatus.PENDING, 2)
        )

        val counts = service.countByStatus()

        AssistanceTicketStatus.values().forEach { status ->
            assertEquals(
                if (status == AssistanceTicketStatus.PENDING) 2L else 0L,
                counts.byStatus[status.name],
                "estado ${status.name}"
            )
        }
    }

    @Test
    fun `resume abiertos, cerrados y total`() {
        every { repository.countGroupedByStatus() } returns listOf(
            row(AssistanceTicketStatus.PENDING, 5),
            row(AssistanceTicketStatus.ASSIGNED, 2),
            row(AssistanceTicketStatus.IN_PROGRESS, 1),
            row(AssistanceTicketStatus.REOPEN, 1),
            row(AssistanceTicketStatus.RESOLVED, 7),
            row(AssistanceTicketStatus.CLOSED, 30),
            row(AssistanceTicketStatus.CANCELLED, 4)
        )

        val counts = service.countByStatus()

        assertEquals(9L, counts.open)
        assertEquals(41L, counts.closed)
        assertEquals(50L, counts.total)
    }

    @Test
    fun `sin tickets devuelve todo en cero`() {
        every { repository.countGroupedByStatus() } returns emptyList()

        val counts = service.countByStatus()

        assertEquals(0L, counts.total)
        assertEquals(0L, counts.open)
        assertEquals(0L, counts.closed)
        assertEquals(AssistanceTicketStatus.values().size, counts.byStatus.size)
    }
}
