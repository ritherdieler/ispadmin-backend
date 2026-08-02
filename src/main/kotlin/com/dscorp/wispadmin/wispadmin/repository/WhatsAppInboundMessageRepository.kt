package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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

    fun findByPhoneAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        phone: String,
        from: LocalDateTime,
        to: LocalDateTime,
        pageable: Pageable
    ): List<WhatsAppInboundMessage>

    fun findTop1ByPhoneOrderByCreatedAtDesc(phone: String): List<WhatsAppInboundMessage>

    fun findByPhoneAndReadAtIsNull(phone: String): List<WhatsAppInboundMessage>

    fun countByPhone(phone: String): Long

    fun countByPhoneAndCreatedAtAfter(phone: String, createdAt: LocalDateTime): Long
}
