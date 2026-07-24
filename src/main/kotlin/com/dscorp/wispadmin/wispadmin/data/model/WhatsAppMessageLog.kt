package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "whatsapp_message_log")
data class WhatsAppMessageLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    var paymentId: Int? = null,

    var subscriptionId: Int? = null,

    var phone: String? = null,

    var messageType: String = "PAYMENT_REMINDER",

    var status: String = "PENDING",

    @Column(length = 500)
    var metaMessageId: String? = null,

    var deliveryStatus: String? = null,

    var deliveryStatusAt: LocalDateTime? = null,

    @Column(length = 1000)
    var message: String? = null,

    @Column(length = 1000)
    var errorMessage: String? = null,

    var createdAt: LocalDateTime = LocalDateTime.now()
)