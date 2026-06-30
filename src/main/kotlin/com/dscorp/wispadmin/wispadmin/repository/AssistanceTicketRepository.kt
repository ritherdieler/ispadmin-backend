package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Date

interface AssistanceTicketRepository : JpaRepository<AssistanceTicket, Int> {

    @Query(
        """
        SELECT t
        FROM AssistanceTicket t
        WHERE t.status = :status
        ORDER BY COALESCE(t.scheduledAt, t.createdAt) ASC
        """
    )
    fun findTop40ByStatusOrderByScheduledAt(
        @Param("status") status: AssistanceTicketStatus
    ): List<AssistanceTicket>

    @Query(
        """
        SELECT t
        FROM AssistanceTicket t
        WHERE t.status = :status
          AND COALESCE(t.scheduledAt, t.createdAt) BETWEEN :start AND :end
        ORDER BY COALESCE(t.scheduledAt, t.createdAt) ASC
        """
    )
    fun findAllByStatusAndScheduledAtBetween(
        @Param("status") status: AssistanceTicketStatus,
        @Param("start") start: Date,
        @Param("end") end: Date
    ): List<AssistanceTicket>
}