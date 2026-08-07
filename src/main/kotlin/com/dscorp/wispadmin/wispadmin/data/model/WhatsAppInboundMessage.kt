package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(
    name = "whatsapp_inbound_message",
    indexes = [
        Index(name = "idx_wainbound_phone", columnList = "phone"),
        Index(name = "idx_wainbound_subscription", columnList = "subscriptionId"),
        Index(name = "idx_wainbound_created", columnList = "createdAt")
    ]
)
data class WhatsAppInboundMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(length = 500, unique = true, nullable = false)
    var metaMessageId: String = "",

    @Column(nullable = false)
    var phone: String = "",

    @Column(length = 2000)
    var messageText: String? = null,

    var messageType: String = "text",

    var subscriptionId: Int? = null,

    var processed: Boolean = false,

    var replySent: Boolean = false,

    @Column(length = 128)
    var buttonReplyId: String? = null,

    @Column(length = 512)
    var buttonReplyTitle: String? = null,

    @Column(length = 255)
    var mediaId: String? = null,

    @Column(length = 128)
    var mediaMimeType: String? = null,

    @Column(length = 1024)
    var mediaStoredPath: String? = null,

    @Column(length = 255)
    var contextMessageId: String? = null,

    var replyToLogId: Int? = null,

    @Column(length = 1000)
    var errorMessage: String? = null,

    var readAt: LocalDateTime? = null,

    var createdAt: LocalDateTime = LocalDateTime.now(),

    /** Emoji reaction applied by an agent/business on this inbound message. */
    @Column(name = "agent_reaction_emoji", length = 16)
    var agentReactionEmoji: String? = null
)
