package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.FixedCost
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FixedCostRepository : JpaRepository<FixedCost, Int> {
    fun findByEnabledTrue(): List<FixedCost>

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FixedCost f")
    fun sumAllAmounts(): Double
}
