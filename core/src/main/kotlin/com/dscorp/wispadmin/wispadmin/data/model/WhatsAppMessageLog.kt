package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(
    name = "whatsapp_message_log",
    indexes = [
        Index(name = "uk_whatsapp_message_log_meta_message_id", columnList = "metaMessageId", unique = true),
        Index(name = "idx_wa_message_log_phone_created", columnList = "phone,createdAt"),
        Index(name = "idx_wa_message_log_created", columnList = "createdAt"),
        Index(name = "idx_wa_message_log_callback_id", columnList = "callbackId"),
        Index(name = "idx_wa_message_log_subscription_type_created", columnList = "subscriptionId,messageType,createdAt"),
        Index(name = "idx_wa_message_log_payment_type_created", columnList = "paymentId,messageType,createdAt"),
        Index(name = "idx_wa_message_log_campaign_id", columnList = "campaignId"),
        Index(name = "idx_wa_message_log_type_created", columnList = "messageType,createdAt")
    ]
)
data class WhatsAppMessageLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    var paymentId: Int? = null,

    var subscriptionId: Int? = null,

    var phone: String? = null,

    var messageType: String = "PAYMENT_REMINDER",

    var status: String = "PENDING",

    @Column(length = 500, unique = true)
    var metaMessageId: String? = null,

    var deliveryStatus: String? = null,

    var deliveryStatusAt: LocalDateTime? = null,

    var sentAt: LocalDateTime? = null,

    var deliveredAt: LocalDateTime? = null,

    var readAt: LocalDateTime? = null,

    var failedAt: LocalDateTime? = null,

    @Column(length = 255)
    var conversationId: String? = null,

    @Column(length = 64)
    var conversationCategory: String? = null,

    var billable: Boolean? = null,

    @Column(length = 32)
    var pricingModel: String? = null,

    @Column(length = 64)
    var campaignId: String? = null,

    @Column(length = 128)
    var operatorUsername: String? = null,

    @Column(length = 1000)
    var message: String? = null,

    @Column(length = 1000)
    var errorMessage: String? = null,

    var replyToLogId: Int? = null,

    @Column(length = 255)
    var mediaMetaId: String? = null,

    @Column(length = 128)
    var mediaMimeType: String? = null,

    @Column(length = 1024)
    var mediaStoredPath: String? = null,

    @Column
    var mediaPurgedAt: LocalDateTime? = null,

    @Column(length = 255)
    var mediaFilename: String? = null,

    var retryCount: Int = 0,

    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(length = 64)
    var callbackId: String? = null,

    /** Emoji reaction applied by the WhatsApp user on this outbound message. */
    @Column(name = "customer_reaction_emoji", length = 16)
    var customerReactionEmoji: String? = null,

    @Column(name = "edited_at")
    var editedAt: LocalDateTime? = null,

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,

    @Column(name = "original_message", length = 1000)
    var originalMessage: String? = null
)
