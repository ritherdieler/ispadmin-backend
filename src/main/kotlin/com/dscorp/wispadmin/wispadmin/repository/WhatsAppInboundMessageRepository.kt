package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface WhatsAppInboundMessageRepository : JpaRepository<WhatsAppInboundMessage, Int> {

    fun existsByMetaMessageId(metaMessageId: String): Boolean

    fun findByMetaMessageId(metaMessageId: String): WhatsAppInboundMessage?

    fun findTop50ByOrderByCreatedAtDesc(): List<WhatsAppInboundMessage>

    fun findTop500ByOrderByCreatedAtDesc(): List<WhatsAppInboundMessage>

    fun findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId: Int): List<WhatsAppInboundMessage>

    fun findByCreatedAtBetween(from: LocalDateTime, to: LocalDateTime): List<WhatsAppInboundMessage>

    fun findByPhoneOrderByCreatedAtAsc(phone: String): List<WhatsAppInboundMessage>

    fun findByPhoneOrderByCreatedAtDesc(phone: String, pageable: Pageable): List<WhatsAppInboundMessage>

    fun findByPhoneAndCreatedAtLessThanOrderByCreatedAtDesc(
        phone: String,
        before: LocalDateTime,
        pageable: Pageable
    ): List<WhatsAppInboundMessage>

    fun findByPhoneAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        phone: String,
        from: LocalDateTime,
        to: LocalDateTime,
        pageable: Pageable
    ): List<WhatsAppInboundMessage>

    fun findTop1ByPhoneOrderByCreatedAtDesc(phone: String): List<WhatsAppInboundMessage>

    fun findTop1ByPhoneInOrderByCreatedAtDesc(phones: Collection<String>): List<WhatsAppInboundMessage>

    fun findByPhoneIn(phones: Collection<String>): List<WhatsAppInboundMessage>

    fun findByPhoneAndReadAtIsNull(phone: String): List<WhatsAppInboundMessage>

    fun countByPhone(phone: String): Long

    fun countByPhoneAndReadAtIsNull(phone: String): Long

    fun countByPhoneAndCreatedAtAfter(phone: String, createdAt: LocalDateTime): Long

    @Query(
        value = """
        SELECT phone FROM (
            SELECT phone, MAX(created_at) AS last_at
            FROM whatsapp_inbound_message
            GROUP BY phone
            UNION ALL
            SELECT phone, MAX(created_at) AS last_at
            FROM whatsapp_message_log
            WHERE phone IS NOT NULL
            GROUP BY phone
        ) combined
        GROUP BY phone
        ORDER BY MAX(last_at) DESC
        LIMIT :limit
    """,
        nativeQuery = true
    )
    fun findRecentActivePhones(@Param("limit") limit: Int): List<String>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WhatsAppInboundMessage m SET m.readAt = :readAt WHERE m.id IN :ids AND m.readAt IS NULL")
    fun markReadByIds(@Param("ids") ids: Collection<Int>, @Param("readAt") readAt: LocalDateTime): Int
}
