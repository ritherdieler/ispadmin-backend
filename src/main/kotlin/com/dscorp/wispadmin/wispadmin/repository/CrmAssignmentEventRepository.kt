package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.CrmAssignmentEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CrmAssignmentEventRepository : JpaRepository<CrmAssignmentEvent, Long> {
    fun findByConversationIdOrderByCreatedAtDesc(conversationId: Long): List<CrmAssignmentEvent>

    fun findByCreatedAtBetween(from: java.time.LocalDateTime, to: java.time.LocalDateTime): List<CrmAssignmentEvent>
}
