package com.dscorp.wispadmin.wispadmin.dto

import java.time.LocalDateTime

data class CrmMetricsCsatReferenceDto(
    val panelPath: String,
    val responseRate: Double?,
    val averageScore: Double?,
    val answeredSurveys: Long
)

data class CrmMetricsIntentCountDto(
    val intent: String,
    val count: Long
)

data class CrmMetricsSampleBucketDto(
    val key: String,
    val label: String,
    val sampleSize: Long,
    val avgFirstResponseMinutes: Double?,
    val avgResolutionMinutes: Double?,
    val resolvedCount: Long
)

data class CrmMetricsExcessiveWaitDto(
    val conversationId: Long,
    val phone: String,
    val waitMinutes: Long,
    val status: String
)

data class CrmMetricsSummaryDto(
    val from: LocalDateTime,
    val to: LocalDateTime,
    val resolvedConversations: Long,
    val pendingUnassigned: Long,
    val assignedActive: Long,
    val reopenEvents: Long,
    val transferEvents: Long,
    val avgFirstResponseMinutes: Double?,
    val avgResolutionMinutes: Double?,
    val firstResponseWithinSlaRate: Double?,
    val resolutionWithinSlaRate: Double?,
    val abandonmentCount: Long,
    val botToHumanTransfers: Long,
    val autoResolutionRate: Double?,
    val ticketsFromConversations: Long,
    val linkedTicketsSlaBreached: Long,
    val recurringCustomerPhones: Long,
    val contactReasons: List<CrmMetricsIntentCountDto>,
    val byAgent: List<CrmMetricsSampleBucketDto>,
    val byPlace: List<CrmMetricsSampleBucketDto>,
    val byCategory: List<CrmMetricsSampleBucketDto>,
    val csatReference: CrmMetricsCsatReferenceDto
)

data class CrmMetricsAgentLoadDto(
    val agentId: Int,
    val agentLabel: String,
    val resolvedCount: Long,
    val activeAssignedCount: Long,
    val transferInCount: Long,
    val transferOutCount: Long
)

data class CrmMetricsShiftHandoffDto(
    val from: LocalDateTime,
    val to: LocalDateTime,
    val pendingUnassigned: Long,
    val reopenEvents: Long,
    val botToHumanTransfers: Long,
    val resolvedInWindow: Long,
    val excessiveWaitAlerts: List<CrmMetricsExcessiveWaitDto>
)
