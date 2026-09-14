package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.Outlay
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Date

interface OutlayRepository : JpaRepository<Outlay, Int> {
    fun findByDateGreaterThanEqualAndDateLessThanEqual(
        firstDayOfMonthInMillis: Date,
        lastDayOfMonthInMillis: Date
    ): List<Outlay>
    
    fun findByDateBetween(startDate: Date, endDate: Date, pageable: Pageable): Page<Outlay>

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM Outlay o WHERE o.date >= :startDate AND o.date <= :endDate")
    fun sumAmountByDateBetween(startDate: Date, endDate: Date): Double
}
