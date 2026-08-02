package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppChatState
import org.springframework.data.jpa.repository.JpaRepository

interface WhatsAppChatStateRepository : JpaRepository<WhatsAppChatState, Int> {
    fun findByPhone(phone: String): WhatsAppChatState?
}
