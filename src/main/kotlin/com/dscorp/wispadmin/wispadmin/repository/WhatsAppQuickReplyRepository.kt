package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppQuickReply
import org.springframework.data.jpa.repository.JpaRepository

interface WhatsAppQuickReplyRepository : JpaRepository<WhatsAppQuickReply, Long> {
    fun existsByShortcutIgnoreCase(shortcut: String): Boolean
    fun existsByShortcutIgnoreCaseAndIdNot(shortcut: String, id: Long): Boolean
}
