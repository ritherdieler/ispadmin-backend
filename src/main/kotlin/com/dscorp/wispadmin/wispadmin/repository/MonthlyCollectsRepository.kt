package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.*
import org.springframework.data.jpa.repository.JpaRepository

interface MonthlyCollectsRepository : JpaRepository<MonthlyCollectsResume, Int> {
 fun findTop8ByOrderByDateDesc(): List<MonthlyCollectsResume>

}