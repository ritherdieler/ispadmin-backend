package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.SessionWorkflowSummaryDto
import com.dscorp.wispadmin.observability.entity.ObsEvent

object ObsWorkflowAggregation {

    fun fromEvents(events: List<ObsEvent>): List<SessionWorkflowSummaryDto> {
        val grouped = events
            .filter { !it.workflowId.isNullOrBlank() }
            .groupBy { it.workflowId!! }

        return grouped.map { (workflowId, rows) ->
            val ordered = rows.sortedBy { it.createdAt ?: it.eventTimestamp }
            val lastStatus = ordered
                .mapNotNull { it.workflowStatus }
                .lastOrNull()
                ?: if (ordered.any { it.eventType.equals("workflow_end", ignoreCase = true) }) {
                    ordered.lastOrNull { it.eventType.equals("workflow_end", ignoreCase = true) }?.workflowStatus
                } else {
                    "interrupted"
                }
            val name = ordered.mapNotNull { it.workflowName }.lastOrNull()
            val category = ordered.mapNotNull { it.workflowCategory }.lastOrNull()
            SessionWorkflowSummaryDto(
                workflowId = workflowId,
                name = name,
                category = category,
                status = lastStatus ?: "interrupted",
                firstSeen = ordered.mapNotNull { it.createdAt ?: it.eventTimestamp }.minOrNull(),
                lastSeen = ordered.mapNotNull { it.createdAt ?: it.eventTimestamp }.maxOrNull(),
                eventCount = ordered.size.toLong()
            )
        }.sortedByDescending { it.lastSeen }
    }
}
