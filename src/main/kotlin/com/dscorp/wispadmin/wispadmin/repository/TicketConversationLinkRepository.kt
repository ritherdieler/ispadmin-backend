package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.TicketConversationLink
import org.springframework.data.jpa.repository.JpaRepository

interface TicketConversationLinkRepository : JpaRepository<TicketConversationLink, Long> {
    fun findByTicketId(ticketId: Int): TicketConversationLink?
    fun findByConversationIdOrderByCreatedAtDesc(conversationId: Long): List<TicketConversationLink>
    fun findByConversationIdIn(conversationIds: Collection<Long>): List<TicketConversationLink>

    fun findByCreatedAtBetween(from: java.time.LocalDateTime, to: java.time.LocalDateTime): List<TicketConversationLink>
}
