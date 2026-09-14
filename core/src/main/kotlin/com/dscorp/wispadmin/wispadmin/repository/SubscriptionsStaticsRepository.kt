package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Date

interface SubscriptionsStaticsRepository : JpaRepository<MonthlySubscriptionResume, Int> {
    fun findTop10ByOrderByDateDesc(): List<MonthlySubscriptionResume>

    @Query("SELECT m FROM MonthlySubscriptionResume m WHERE m.date >= :from AND m.date < :to")
    fun findByDateInRange(from: Date, to: Date): List<MonthlySubscriptionResume>
}