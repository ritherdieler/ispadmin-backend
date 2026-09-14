package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionLog
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionActionType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.YearMonth
import java.util.*

interface SubscriptionLogRepository : JpaRepository<SubscriptionLog, Int> {
    @Query("""
        SELECT 
            sl.actionType as actionType, 
            COUNT(sl) as count,
            FUNCTION('YEAR', sl.date) as year,
            FUNCTION('MONTH', sl.date) as month
        FROM SubscriptionLog sl 
        WHERE sl.date >= :startDate 
        GROUP BY sl.actionType, FUNCTION('YEAR', sl.date), FUNCTION('MONTH', sl.date)
        ORDER BY FUNCTION('YEAR', sl.date) DESC, FUNCTION('MONTH', sl.date) DESC, COUNT(sl) DESC
    """)
    fun getSubscriptionLogSummary(startDate: Date): List<Map<String, Any>>

    @Query("""
        SELECT sl FROM SubscriptionLog sl
        WHERE sl.actionType = 'CANCELED_BY_STORED_PROCEDURE'
          AND sl.date >= :startDate
          AND sl.date <= :endDate
    """)
    fun getCanceledSubscriptionsBySystem(startDate: Date, endDate: Date): List<SubscriptionLog>

    @Query("""
        SELECT sl FROM SubscriptionLog sl
        WHERE sl.actionType = 'CANCEL_SUBSCRIPTION'
          AND sl.date >= :startDate
          AND sl.date <= :endDate
    """)
    fun getCanceledSubscriptionsByUser(startDate: Date, endDate: Date): List<SubscriptionLog>

    @Query("""
        SELECT sl FROM SubscriptionLog sl
        WHERE sl.actionType = 'RECONNECT_CANCELLED_SUBSCRIPTION'
          AND sl.date >= :startDate
          AND sl.date <= :endDate
    """)
    fun getReconnections(startDate: Date, endDate: Date): List<SubscriptionLog>

}
