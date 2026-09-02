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
        LEFT JOIN t.subscription s
        WHERE t.status = :status
          AND (t.subscription IS NULL OR s.id IS NOT NULL)
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

    fun findByStatusIn(statuses: Collection<AssistanceTicketStatus>): List<AssistanceTicket>

    @Query(
        """
        SELECT t.status, COUNT(t)
        FROM AssistanceTicket t
        GROUP BY t.status
        """
    )
    fun countGroupedByStatus(): List<Array<Any>>

    fun findByPhoneOrderByCreatedAtDesc(phone: String): List<AssistanceTicket>

    @Query(
        """
        SELECT t
        FROM AssistanceTicket t
        WHERE t.phone = :phone
          AND t.category = :category
          AND t.createdAt >= :since
          AND t.status IN :openStatuses
        ORDER BY t.createdAt DESC
        """
    )
    fun findOpenByPhoneAndCategorySince(
        @Param("phone") phone: String,
        @Param("category") category: String,
        @Param("since") since: Date,
        @Param("openStatuses") openStatuses: Collection<AssistanceTicketStatus>
    ): List<AssistanceTicket>
}
