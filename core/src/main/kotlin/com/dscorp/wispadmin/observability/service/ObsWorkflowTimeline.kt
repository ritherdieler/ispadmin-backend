package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.WorkflowTimelineEventDto
import com.dscorp.wispadmin.observability.entity.ObsEvent

object ObsWorkflowTimeline {
    fun from(events: List<ObsEvent>): List<WorkflowTimelineEventDto> = events
        .sortedWith(compareBy<ObsEvent> { it.createdAt }.thenBy { it.id })
        .map { event ->
            WorkflowTimelineEventDto(
                id = event.id,
                createdAt = event.createdAt,
                platform = event.platform,
                eventType = event.eventType,
                severity = event.severity,
                message = event.message?.take(MAX_MESSAGE_LENGTH),
                errorType = event.errorType?.take(MAX_MESSAGE_LENGTH),
                workflowStatus = event.workflowStatus,
                httpStatus = event.httpStatus,
                url = event.url?.substringBefore('?'),
                correlationId = event.correlationId,
                issueId = event.issueId,
            )
        }

    private const val MAX_MESSAGE_LENGTH = 500
}
