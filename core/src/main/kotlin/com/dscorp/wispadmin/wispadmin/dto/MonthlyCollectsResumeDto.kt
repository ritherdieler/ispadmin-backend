package com.dscorp.wispadmin.wispadmin.dto

import java.util.*

data class MonthlyCollectsResumeDto(
    val grossIncome:Double,
    val totalRaised: Double,
    val totalDiscount: Double,
    val totalReceivables: Double,
    val date: Date
)