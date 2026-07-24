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

    @Column(length = 1000)
    var errorMessage: String? = null,

    var createdAt: LocalDateTime = LocalDateTime.now()
)
