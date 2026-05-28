package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.MonthlyCollectsResumeDto
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity
data class MonthlyCollectsResume(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int,
    val grossIncome:Double,
    val totalRaised: Double,
    val totalDiscount: Double,
    val totalReceivables: Double,
    val date: Date
){
    fun Double.asPercent(): Double {
        val percentage = this * 100 / grossIncome
        return String.format("%.1f", percentage).toDouble()
    }
}

fun MonthlyCollectsResume.toDto() = MonthlyCollectsResumeDto(
    date = date, totalRaised = totalRaised.asPercent(), totalDiscount = totalDiscount.asPercent(), totalReceivables = totalReceivables.asPercent(), grossIncome = grossIncome
)

fun MonthlyCollectsResume.toDtoAsCurrency() = MonthlyCollectsResumeDto(
    date = date, totalRaised = totalRaised, totalDiscount = totalDiscount, totalReceivables = totalReceivables, grossIncome = grossIncome
)







