package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(
    name = "whatsapp_account_event",
    indexes = [
        Index(name = "idx_wa_account_event_type", columnList = "eventType"),
        Index(name = "idx_wa_account_event_created", columnList = "createdAt")
    ]
)
data class WhatsAppAccountEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(length = 128, nullable = false)
    var eventType: String = "",

    @Column(length = 512, unique = true, nullable = false)
    var eventKey: String = "",

    @Column(length = 255)
    var templateName: String? = null,

    @Column(length = 64)
    var templateId: String? = null,

    @Column(length = 32)
    var severity: String? = null,

    @Column(length = 32)
    var qualityScore: String? = null,

    @Column(length = 4000)
    var payloadSummary: String? = null,

    var createdAt: LocalDateTime = LocalDateTime.now()
)
