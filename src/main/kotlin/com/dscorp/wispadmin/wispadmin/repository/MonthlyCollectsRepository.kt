package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Date

interface MonthlyCollectsRepository : JpaRepository<MonthlyCollectsResume, Int> {
    fun findTop8ByOrderByDateDesc(): List<MonthlyCollectsResume>

    @Query("SELECT m FROM MonthlyCollectsResume m WHERE m.date >= :from AND m.date < :to")
    fun findByDateInRange(from: Date, to: Date): List<MonthlyCollectsResume>
}