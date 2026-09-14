package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.data.model.FixedCost
import com.dscorp.wispadmin.wispadmin.data.model.FixedCostType
import com.dscorp.wispadmin.wispadmin.data.model.User

data class FixedCostRequest(
    val amount: Double,
    val description: String,
    val note: String,
    val type: FixedCostType,
    val userId: Int
) {
    fun toFixedCost(user: User) = FixedCost(
        amount = amount,
        description = description,
        note = note,
        type = type,
        user = user
    )
}