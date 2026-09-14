package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table
import javax.persistence.UniqueConstraint

@Entity
@Table(
    name = "ticket_conversation_link",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_ticket_conversation_ticket", columnNames = ["ticket_id"]),
        UniqueConstraint(name = "uk_ticket_conversation_pair", columnNames = ["ticket_id", "conversation_id"])
    ],
    indexes = [
        Index(name = "idx_ticket_conversation_conversation", columnList = "conversation_id")
    ]
)
data class TicketConversationLink(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "ticket_id", nullable = false)
    var ticketId: Int = 0,

    @Column(name = "conversation_id", nullable = false)
    var conversationId: Long = 0,

    @Column(name = "created_by", length = 128)
    var createdBy: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
