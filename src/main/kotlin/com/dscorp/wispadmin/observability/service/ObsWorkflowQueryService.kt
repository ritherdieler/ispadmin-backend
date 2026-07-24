package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.dto.WorkflowSummaryDto
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

@Service
class ObsWorkflowQueryService(
    private val eventRepository: ObsEventRepository
) {

    fun listWorkflows(
        from: LocalDateTime,
        to: LocalDateTime,
        release: String?,
        platform: String?,
        status: String?,
        page: Int,
        size: Int
    ): PagedResponse<WorkflowSummaryDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200))
        val result = eventRepository.aggregateWorkflows(
            from = from,
            to = to,
            release = release?.takeIf { it.isNotBlank() },
            platform = platform?.takeIf { it.isNotBlank() },
            status = status?.takeIf { it.isNotBlank() },
            pageable = pageable
        )

        val content = result.content.map { row ->
            WorkflowSummaryDto(
                workflowId = row[0]?.toString().orEmpty(),
                name = row[1]?.toString(),
                category = row[2]?.toString(),
                status = row[3]?.toString(),
                platform = row[4]?.toString(),
                sessionId = row[5]?.toString(),
                eventCount = (row[6] as Number).toLong(),
                lastSeen = toLocalDateTime(row[7]),
                firstSeen = toLocalDateTime(row[8])
            )
        }

        return PagedResponse(
            content = content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    private fun toLocalDateTime(value: Any?): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        is Timestamp -> value.toLocalDateTime()
        else -> null
    }
}
