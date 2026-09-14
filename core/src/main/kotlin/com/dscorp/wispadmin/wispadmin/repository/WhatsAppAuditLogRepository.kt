package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppAuditLog
import org.springframework.data.jpa.repository.JpaRepository

interface WhatsAppAuditLogRepository : JpaRepository<WhatsAppAuditLog, Long> {
    fun findByActionAndCreatedAtBetween(
        action: String,
        from: java.time.LocalDateTime,
        to: java.time.LocalDateTime
    ): List<WhatsAppAuditLog>
}
