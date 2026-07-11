package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import org.springframework.data.jpa.repository.JpaRepository

interface WhatsAppInboundMessageRepository : JpaRepository<WhatsAppInboundMessage, Int> {

    fun existsByMetaMessageId(metaMessageId: String): Boolean

    fun findByMetaMessageId(metaMessageId: String): WhatsAppInboundMessage?

    fun findTop50ByOrderByCreatedAtDesc(): List<WhatsAppInboundMessage>

    fun findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId: Int): List<WhatsAppInboundMessage>
}
