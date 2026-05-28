package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.*
import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionsStaticsRepository : JpaRepository<MonthlySubscriptionResume, Int> {
    //get all limit 4
    fun findTop10ByOrderByDateDesc(): List<MonthlySubscriptionResume>

}