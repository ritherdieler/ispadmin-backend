package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.FixedCostType
import java.util.Date

data class FixedCostDto(
    val id: Int,
    val amount: Double,
    val description: String,
    val note: String,
    val type: FixedCostType,
    val userId: Int,
    val date: Date
)