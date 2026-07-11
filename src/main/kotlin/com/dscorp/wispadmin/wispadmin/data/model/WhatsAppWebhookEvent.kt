package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(
    name = "whatsapp_webhook_event",
    indexes = [Index(name = "idx_wahook_created", columnList = "createdAt")]
)
data class WhatsAppWebhookEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(length = 500, unique = true, nullable = false)
    var eventKey: String = "",

    @Column(nullable = false)
    var eventType: String = "",

    @Column(length = 4000)
    var payloadSummary: String? = null,

    var createdAt: LocalDateTime = LocalDateTime.now()
)
