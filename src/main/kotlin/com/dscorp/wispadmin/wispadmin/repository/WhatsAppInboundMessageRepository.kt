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
        HAVING MAX(last_at) < :cursor
        ORDER BY MAX(last_at) DESC
        LIMIT :limit
    """,
        nativeQuery = true
    )
    fun findRecentActivePhonesBefore(
        @Param("limit") limit: Int,
        @Param("cursor") cursor: LocalDateTime
    ): List<String>

    @Query(
        value = """
        SELECT m.*
        FROM whatsapp_inbound_message m
        INNER JOIN (
            SELECT phone, MAX(created_at) AS max_at
            FROM whatsapp_inbound_message
            WHERE phone IN (:phones)
            GROUP BY phone
        ) latest ON latest.phone = m.phone AND latest.max_at = m.created_at
        WHERE m.phone IN (:phones)
        """,
        nativeQuery = true
    )
    fun findLatestInboundByPhoneIn(@Param("phones") phones: Collection<String>): List<WhatsAppInboundMessage>

    @Query(
        value = """
        SELECT phone, COUNT(*) AS unread_count
        FROM whatsapp_inbound_message
        WHERE phone IN (:phones) AND read_at IS NULL
        GROUP BY phone
        """,
        nativeQuery = true
    )
    fun countUnreadByPhoneIn(@Param("phones") phones: Collection<String>): List<Array<Any>>

    @Query(
        value = """
        SELECT phone, MAX(created_at) AS latest_media_at
        FROM whatsapp_inbound_message
        WHERE phone IN (:phones)
          AND (
            LOWER(message_type) = 'image'
            OR (
              LOWER(message_type) = 'document'
              AND LOWER(COALESCE(media_mime_type, '')) LIKE 'application/pdf%'
            )
          )
        GROUP BY phone
        """,
        nativeQuery = true
    )
    fun findLatestMediaAtByPhoneIn(@Param("phones") phones: Collection<String>): List<Array<Any>>

    @Query(
        value = """
        SELECT phone, subscription_id
        FROM whatsapp_inbound_message
        WHERE id IN (
            SELECT MAX(id)
            FROM whatsapp_inbound_message
            WHERE phone IN (:phones) AND subscription_id IS NOT NULL
            GROUP BY phone
        )
        """,
        nativeQuery = true
    )
    fun findLatestSubscriptionIdByPhoneIn(@Param("phones") phones: Collection<String>): List<Array<Any>>

    @Query(
        value = """
        SELECT phone, button_reply_id
        FROM whatsapp_inbound_message
        WHERE id IN (
            SELECT MAX(id)
            FROM whatsapp_inbound_message
            WHERE phone IN (:phones)
              AND button_reply_id IS NOT NULL
              AND button_reply_id <> ''
            GROUP BY phone
        )
        """,
        nativeQuery = true
    )
    fun findLatestButtonReplyIdByPhoneIn(@Param("phones") phones: Collection<String>): List<Array<Any>>

    @Query(
        value = """
        SELECT COUNT(*)
        FROM whatsapp_inbound_message
        WHERE read_at IS NULL
        """,
        nativeQuery = true
    )
    fun countAllUnread(): Long

    @Query(
        value = """
        SELECT COUNT(DISTINCT phone)
        FROM whatsapp_inbound_message
        WHERE read_at IS NULL
        """,
        nativeQuery = true
    )
    fun countPhonesWithUnread(): Long

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WhatsAppInboundMessage m SET m.readAt = :readAt WHERE m.id IN :ids AND m.readAt IS NULL")
    fun markReadByIds(@Param("ids") ids: Collection<Int>, @Param("readAt") readAt: LocalDateTime): Int
}
