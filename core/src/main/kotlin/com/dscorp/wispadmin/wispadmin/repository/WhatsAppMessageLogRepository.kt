package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    fun findByPhoneAndCreatedAtLessThanOrderByCreatedAtDesc(
        phone: String,
        before: LocalDateTime,
        pageable: Pageable
    ): List<WhatsAppMessageLog>

    fun findByPhoneAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        phone: String,
        from: LocalDateTime,
        to: LocalDateTime,
        pageable: Pageable
    ): List<WhatsAppMessageLog>

    fun findTop10ByPhoneOrderByCreatedAtDesc(phone: String): List<WhatsAppMessageLog>

    fun findByPhoneInOrderByCreatedAtDesc(phones: Collection<String>, pageable: Pageable): List<WhatsAppMessageLog>

    fun findByPhoneIn(phones: Collection<String>): List<WhatsAppMessageLog>

    @Query(
        value = """
        SELECT m.*
        FROM whatsapp_message_log m
        INNER JOIN (
            SELECT phone, MAX(created_at) AS max_at
            FROM whatsapp_message_log
            WHERE phone IN (:phones)
            GROUP BY phone
        ) latest ON latest.phone = m.phone AND latest.max_at = m.created_at
        WHERE m.phone IN (:phones)
        """,
        nativeQuery = true
    )
    fun findLatestOutboundByPhoneIn(@Param("phones") phones: Collection<String>): List<WhatsAppMessageLog>

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

    fun existsByPhoneAndMessageTypeAndMessageStartingWithAndCreatedAtAfter(
        phone: String,
        messageType: String,
        message: String,
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

    @Query(
        """
        SELECT DISTINCT l.paymentId FROM WhatsAppMessageLog l
        WHERE l.paymentId IN :paymentIds
          AND l.messageType = :messageType
          AND l.status = :status
          AND l.createdAt BETWEEN :startDate AND :endDate
        """
    )
    fun findPaymentIdsSentToday(
        @Param("paymentIds") paymentIds: Collection<Int>,
        @Param("messageType") messageType: String,
        @Param("status") status: String,
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): Set<Int>

    @Query(
        """
        SELECT DISTINCT l.subscriptionId FROM WhatsAppMessageLog l
        WHERE l.subscriptionId IN :subscriptionIds
          AND l.messageType = :messageType
          AND l.status = :status
          AND l.createdAt BETWEEN :startDate AND :endDate
        """
    )
    fun findSubscriptionIdsSentToday(
        @Param("subscriptionIds") subscriptionIds: Collection<Int>,
        @Param("messageType") messageType: String,
        @Param("status") status: String,
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): Set<Int>

    @Query(
        """
        SELECT m FROM WhatsAppMessageLog m
        WHERE m.mediaStoredPath IS NOT NULL
          AND m.mediaPurgedAt IS NULL
          AND m.createdAt < :before
        ORDER BY m.createdAt ASC
        """
    )
    fun findOutboundMediaRetentionCandidates(
        @Param("before") before: LocalDateTime,
        pageable: Pageable
    ): List<WhatsAppMessageLog>
}