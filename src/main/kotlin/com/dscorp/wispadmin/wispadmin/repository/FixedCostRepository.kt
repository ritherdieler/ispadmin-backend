package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.FixedCost
import org.springframework.data.jpa.repository.JpaRepository

interface FixedCostRepository : JpaRepository<FixedCost, Int> {
    fun findByEnabledTrue(): List<FixedCost>
}