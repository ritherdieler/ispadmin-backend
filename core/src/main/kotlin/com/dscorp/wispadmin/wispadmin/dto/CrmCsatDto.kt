package com.dscorp.wispadmin.wispadmin.dto

import java.time.LocalDateTime

data class CsatSurveyDto(
    val id: Long,
    val ticketId: Int,
    val phone: String,
    val status: String,
    val score: Int?,
    val comment: String?,
    val dissatisfactionReason: String?,
    val technicianId: Int?,
    val placeName: String?,
    val ticketCategory: String?,
    val sendChannel: String?,
    val retries: Int,
    val scheduledAt: LocalDateTime,
    val sentAt: LocalDateTime?,
    val expiresAt: LocalDateTime,
    val respondedAt: LocalDateTime?,
    val lastError: String?
)

data class CsatFollowUpDto(
    val id: Long,
    val surveyId: Long,
    val ticketId: Int?,
    val phone: String?,
    val score: Int?,
    val reason: String?,
    val status: String,
    val assignedTo: Int?,
    val actions: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val closedAt: LocalDateTime?
)

data class CsatFollowUpUpdateBody(
    val status: String? = null,
    val assignedTo: Int? = null,
    val actions: String? = null,
    val reason: String? = null
)

data class CsatSampleBucketDto(
    val key: String,
    val label: String,
    val averageScore: Double?,
    val answered: Long,
    val sampleSize: Long
)

data class CsatEvolutionPointDto(
    val date: String,
    val averageScore: Double?,
    val answered: Long,
    val sent: Long
)

data class CsatReasonCountDto(
    val reason: String,
    val count: Long
)

data class CsatSummaryDto(
    val from: LocalDateTime,
    val to: LocalDateTime,
    val scheduled: Long,
    val sent: Long,
    val answered: Long,
    val expired: Long,
    val failed: Long,
    val responseRate: Double,
    val averageScore: Double?,
    val byTechnician: List<CsatSampleBucketDto>,
    val byPlace: List<CsatSampleBucketDto>,
    val byCategory: List<CsatSampleBucketDto>,
    val evolution: List<CsatEvolutionPointDto>,
    val reasons: List<CsatReasonCountDto>
)
