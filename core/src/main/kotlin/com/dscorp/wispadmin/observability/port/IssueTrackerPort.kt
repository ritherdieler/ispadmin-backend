package com.dscorp.wispadmin.observability.port

import com.dscorp.wispadmin.observability.entity.ObsIssueStatus

interface IssueTrackerPort {
    val providerId: String
    val providerLabel: String
    fun isConfigured(): Boolean
    fun testConnection(): TrackerTestResult
    fun createTicket(request: CreateTicketRequest): CreateTicketResult
    fun parseWebhook(headers: Map<String, String>, rawBody: String): IncomingTrackerEvent?
}

data class CreateTicketRequest(
    val summary: String,
    val descriptionText: String,
    val severity: String?
)

data class CreateTicketResult(
    val ok: Boolean,
    val issueKey: String?,
    val browseUrl: String?,
    val error: String?
)

data class TrackerTestResult(
    val ok: Boolean,
    val message: String,
    val accountName: String?
)

enum class TrackerEventType {
    TICKET_DELETED,
    TICKET_STATUS_CHANGED
}

data class IncomingTrackerEvent(
    val provider: String,
    val type: TrackerEventType,
    val ticketKey: String?,
    val rawStatus: String? = null,
    val mappedStatus: ObsIssueStatus? = null
)
