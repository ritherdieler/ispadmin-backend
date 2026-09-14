package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppAccountEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface WhatsAppAccountEventRepository : JpaRepository<WhatsAppAccountEvent, Int> {

    fun existsByEventKey(eventKey: String): Boolean

    fun findTop20ByOrderByCreatedAtDesc(): List<WhatsAppAccountEvent>

    fun findByEventTypeOrderByCreatedAtDesc(eventType: String): List<WhatsAppAccountEvent>

    fun findByCreatedAtAfterOrderByCreatedAtDesc(since: LocalDateTime): List<WhatsAppAccountEvent>
}
