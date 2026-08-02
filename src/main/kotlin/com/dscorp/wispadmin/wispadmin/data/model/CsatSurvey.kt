package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table
import javax.persistence.UniqueConstraint

enum class CsatSurveyStatus {
    SCHEDULED,
    SENT,
    ANSWERED,
    EXPIRED,
    FAILED
}

enum class CsatSendChannel {
    INTERACTIVE,
    TEMPLATE
}

enum class CsatDissatisfactionReason {
    PUNTUALIDAD,
    TRATO,
    NO_RESUELTO,
    CALIDAD,
    INCUMPLIMIENTO_VISITA,
    OTRO
}

@Entity
@Table(
    name = "csat_survey",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_csat_survey_ticket", columnNames = ["ticket_id"]),
        UniqueConstraint(name = "uk_csat_survey_send_idem", columnNames = ["send_idempotency_key"]),
        UniqueConstraint(name = "uk_csat_survey_capture_idem", columnNames = ["capture_idempotency_key"])
    ],
    indexes = [
        Index(name = "idx_csat_survey_status_next", columnList = "status, next_attempt_at"),
        Index(name = "idx_csat_survey_expires", columnList = "status, expires_at"),
        Index(name = "idx_csat_survey_phone_status", columnList = "phone, status")
    ]
)
data class CsatSurvey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "ticket_id", nullable = false)
    var ticketId: Int = 0,

    @Column(nullable = false, length = 32)
    var phone: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: CsatSurveyStatus = CsatSurveyStatus.SCHEDULED,

    @Column
    var score: Int? = null,

    @Column(name = "comment_text", length = 1000)
    var comment: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "dissatisfaction_reason", length = 64)
    var dissatisfactionReason: CsatDissatisfactionReason? = null,

    @Column(name = "technician_id")
    var technicianId: Int? = null,

    @Column(name = "place_name", length = 255)
    var placeName: String? = null,

    @Column(name = "ticket_category", length = 255)
    var ticketCategory: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "send_channel", length = 32)
    var sendChannel: CsatSendChannel? = null,

    @Column(name = "meta_message_id", length = 128)
    var metaMessageId: String? = null,

    @Column(name = "send_idempotency_key", nullable = false, length = 80)
    var sendIdempotencyKey: String = "",

    @Column(name = "capture_idempotency_key", length = 128)
    var captureIdempotencyKey: String? = null,

    @Column(nullable = false)
    var retries: Int = 0,

    @Column(name = "max_retries", nullable = false)
    var maxRetries: Int = 3,

    @Column(name = "next_attempt_at")
    var nextAttemptAt: LocalDateTime? = null,

    @Column(name = "scheduled_at", nullable = false)
    var scheduledAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "responded_at")
    var respondedAt: LocalDateTime? = null,

    @Column(name = "last_error", length = 500)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
