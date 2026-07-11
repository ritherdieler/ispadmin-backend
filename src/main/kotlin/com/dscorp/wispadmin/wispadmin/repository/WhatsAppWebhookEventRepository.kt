package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppWebhookEvent
import org.springframework.data.jpa.repository.JpaRepository

interface WhatsAppWebhookEventRepository : JpaRepository<WhatsAppWebhookEvent, Int> {

    fun existsByEventKey(eventKey: String): Boolean
}
