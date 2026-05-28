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



    @Query("SELECT sl FROM SubscriptionLog sl WHERE sl.actionType = 'CANCELED_BY_STORED_PROCEDURE' AND MONTH(sl.date) = MONTH(?1) AND YEAR(sl.date) = YEAR(?1)")
    fun getCanceledSubscriptionsBySystem(date: Date): List<SubscriptionLog>

    @Query("SELECT sl FROM SubscriptionLog sl WHERE sl.actionType = 'CANCEL_SUBSCRIPTION' AND MONTH(sl.date) = MONTH(?1) AND YEAR(sl.date) = YEAR(?1)")
    fun getCanceledSubscriptionsByUser(date: Date): List<SubscriptionLog>

    @Query("SELECT sl FROM SubscriptionLog sl WHERE sl.actionType = 'RECONNECT_CANCELLED_SUBSCRIPTION' AND MONTH(sl.date) = MONTH(?1) AND YEAR(sl.date) = YEAR(?1)")
    fun getReconnections(date: Date): List<SubscriptionLog>

}