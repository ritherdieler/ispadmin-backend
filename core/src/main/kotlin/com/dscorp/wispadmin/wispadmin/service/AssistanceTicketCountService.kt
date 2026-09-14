package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.dto.AssistanceTicketCountsDto
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import org.springframework.stereotype.Service

@Service
class AssistanceTicketCountService(
    private val repository: AssistanceTicketRepository
) {

    fun countByStatus(): AssistanceTicketCountsDto {
        val totals = repository.countGroupedByStatus().mapNotNull { row ->
            val status = row.getOrNull(0) as? AssistanceTicketStatus ?: return@mapNotNull null
            status to ((row.getOrNull(1) as? Number)?.toLong() ?: 0L)
        }.toMap()

        val byStatus = AssistanceTicketStatus.values().associate { it.name to (totals[it] ?: 0L) }
        return AssistanceTicketCountsDto(
            byStatus = byStatus,
            open = OPEN_STATUSES.sumOf { totals[it] ?: 0L },
            closed = CLOSED_STATUSES.sumOf { totals[it] ?: 0L },
            total = totals.values.sum()
        )
    }

    companion object {
        val OPEN_STATUSES = listOf(
            AssistanceTicketStatus.PENDING,
            AssistanceTicketStatus.ASSIGNED,
            AssistanceTicketStatus.IN_PROGRESS,
            AssistanceTicketStatus.REOPEN
        )
        val CLOSED_STATUSES = listOf(
            AssistanceTicketStatus.RESOLVED,
            AssistanceTicketStatus.CLOSED,
            AssistanceTicketStatus.CANCELLED
        )
    }
}
