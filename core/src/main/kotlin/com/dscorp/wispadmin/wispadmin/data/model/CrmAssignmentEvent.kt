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

enum class CrmAssignmentEventType {
    CLAIM,
    RELEASE,
    TRANSFER,
    RESOLVE,
    REOPEN,
    AUTO_ASSIGN
}

@Entity
@Table(
    name = "crm_assignment_event",
    indexes = [
        Index(name = "idx_crm_assignment_conversation", columnList = "conversationId"),
        Index(name = "idx_crm_assignment_created", columnList = "createdAt")
    ]
)
data class CrmAssignmentEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var conversationId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var eventType: CrmAssignmentEventType = CrmAssignmentEventType.CLAIM,

    @Column
    var fromUserId: Int? = null,

    @Column
    var toUserId: Int? = null,

    @Column(length = 1000)
    var note: String? = null,

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
