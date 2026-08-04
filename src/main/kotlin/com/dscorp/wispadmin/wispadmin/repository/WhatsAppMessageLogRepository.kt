package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import org.springframework.data.domain.Pageable
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

    fun findTop500ByOrderByCreatedAtDesc(): List<WhatsAppMessageLog>

    fun findByPhoneOrderByCreatedAtAsc(phone: String): List<WhatsAppMessageLog>

    fun findByPhoneOrderByCreatedAtDesc(phone: String, pageable: Pageable): List<WhatsAppMessageLog>

    fun findByPhoneAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        phone: String,
        from: LocalDateTime,
        to: LocalDateTime,
        pageable: Pageable
    ): List<WhatsAppMessageLog>

    fun findTop10ByPhoneOrderByCreatedAtDesc(phone: String): List<WhatsAppMessageLog>

    fun findByPaymentIdOrderByCreatedAtDesc(paymentId: Int): List<WhatsAppMessageLog>

    fun findByMetaMessageId(metaMessageId: String): WhatsAppMessageLog?

    fun findByCallbackId(callbackId: String): WhatsAppMessageLog?

    fun findByCampaignId(campaignId: String): List<WhatsAppMessageLog>

    fun findByCreatedAtBetween(start: LocalDateTime, end: LocalDateTime): List<WhatsAppMessageLog>

    fun findByMessageTypeAndCreatedAtBetween(
        messageType: String,
        start: LocalDateTime,
        end: LocalDateTime
    ): List<WhatsAppMessageLog>

    fun existsByPhoneAndMessageTypeAndCreatedAtAfter(
        phone: String,
        messageType: String,
        createdAt: LocalDateTime
    ): Boolean

    fun existsByPhoneAndMessageTypeAndStatusAndCreatedAtAfter(
        phone: String,
        messageType: String,
        status: String,
        createdAt: LocalDateTime
    ): Boolean

    fun findByMessageTypeInAndCreatedAtBetween(
        messageTypes: Collection<String>,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<WhatsAppMessageLog>
}