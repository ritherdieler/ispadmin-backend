package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import org.springframework.data.jpa.repository.JpaRepository

interface WhatsAppInboundMessageRepository : JpaRepository<WhatsAppInboundMessage, Int> {

    fun existsByMetaMessageId(metaMessageId: String): Boolean

    fun findByMetaMessageId(metaMessageId: String): WhatsAppInboundMessage?

    fun findTop50ByOrderByCreatedAtDesc(): List<WhatsAppInboundMessage>

    fun findTop500ByOrderByCreatedAtDesc(): List<WhatsAppInboundMessage>

    fun findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId: Int): List<WhatsAppInboundMessage>

    fun findByCreatedAtBetween(from: java.time.LocalDateTime, to: java.time.LocalDateTime): List<WhatsAppInboundMessage>

    fun findByPhoneOrderByCreatedAtAsc(phone: String): List<WhatsAppInboundMessage>

    fun findTop1ByPhoneOrderByCreatedAtDesc(phone: String): List<WhatsAppInboundMessage>

    fun findByPhoneAndReadAtIsNull(phone: String): List<WhatsAppInboundMessage>

    fun countByPhone(phone: String): Long

    fun countByPhoneAndCreatedAtAfter(phone: String, createdAt: java.time.LocalDateTime): Long
}
