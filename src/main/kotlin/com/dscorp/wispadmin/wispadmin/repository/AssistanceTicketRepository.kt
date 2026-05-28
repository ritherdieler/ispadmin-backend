package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Date

interface AssistanceTicketRepository : JpaRepository<AssistanceTicket, Int> {
    fun findTop40ByStatusOrderByCreatedAtDesc(status: AssistanceTicketStatus): List<AssistanceTicket>
    fun findAllByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
        status: AssistanceTicketStatus,
        start: Date,
        end: Date
    ): List<AssistanceTicket>
}