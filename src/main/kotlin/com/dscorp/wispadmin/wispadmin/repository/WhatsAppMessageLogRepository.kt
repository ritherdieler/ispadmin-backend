package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface WhatsAppMessageLogRepository : JpaRepository<WhatsAppMessageLog, Int> {

    fun existsByPaymentIdAndMessageTypeAndStatusAndCreatedAtBetween(
        paymentId: Int,
        messageType: String,
        status: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Boolean

    fun existsBySubscriptionIdAndMessageTypeAndStatusAndCreatedAtBetween(
        subscriptionId: Int,
        messageType: String,
        status: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Boolean

    fun existsBySubscriptionIdAndMessageTypeAndStatus(
        subscriptionId: Int,
        messageType: String,
        status: String
    ): Boolean

    fun findTop50ByOrderByCreatedAtDesc(): List<WhatsAppMessageLog>

    fun findByPaymentIdOrderByCreatedAtDesc(paymentId: Int): List<WhatsAppMessageLog>

    fun findByMetaMessageId(metaMessageId: String): WhatsAppMessageLog?
}