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
import javax.persistence.Version

enum class CrmChannel {
    WHATSAPP
}

enum class CrmConversationStatus {
    NEW,
    PENDING,
    ASSIGNED,
    RESOLVED,
    REOPENED
}

@Entity
@Table(
    name = "crm_conversation",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_crm_conversation_channel_phone", columnNames = ["channel", "phone"])
    ],
    indexes = [
        Index(name = "idx_crm_conversation_status", columnList = "status"),
        Index(name = "idx_crm_conversation_assigned", columnList = "assignedAgentId"),
        Index(name = "idx_crm_conversation_last_inbound", columnList = "lastInboundAt")
    ]
)
data class CrmConversation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var channel: CrmChannel = CrmChannel.WHATSAPP,

    @Column(nullable = false, length = 32)
    var phone: String = "",

    @Column
    var subscriptionId: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: CrmConversationStatus = CrmConversationStatus.NEW,

    @Column
    var assignedAgentId: Int? = null,

    @Column(nullable = false)
    var priority: Int = 0,

    @Column
    var claimedAt: LocalDateTime? = null,

    @Column
    var resolvedAt: LocalDateTime? = null,

    @Column
    var resolvedByAgentId: Int? = null,

    @Column
    var lastInboundAt: LocalDateTime? = null,

    @Column
    var lastOutboundAt: LocalDateTime? = null,

    @Column(length = 4000)
    var handoffSummary: String? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0,

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
