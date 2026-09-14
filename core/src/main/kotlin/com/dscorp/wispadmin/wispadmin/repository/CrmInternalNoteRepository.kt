package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.CrmInternalNote
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CrmInternalNoteRepository : JpaRepository<CrmInternalNote, Long> {
    fun findByConversationIdOrderByCreatedAtDesc(conversationId: Long): List<CrmInternalNote>
}
