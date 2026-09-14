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

enum class CsatFollowUpStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED
}

@Entity
@Table(
    name = "csat_follow_up",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_csat_follow_up_survey", columnNames = ["survey_id"])
    ],
    indexes = [
        Index(name = "idx_csat_follow_up_status", columnList = "status")
    ]
)
data class CsatFollowUp(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "survey_id", nullable = false)
    var surveyId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    var reason: CsatDissatisfactionReason? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: CsatFollowUpStatus = CsatFollowUpStatus.OPEN,

    @Column(name = "assigned_to")
    var assignedTo: Int? = null,

    @Column(length = 2000)
    var actions: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "closed_at")
    var closedAt: LocalDateTime? = null
)
