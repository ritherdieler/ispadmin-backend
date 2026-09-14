package com.dscorp.wispadmin.wispadmin.requestbody

data class UpdateSubscriptionPlanBody(
    val planId: Int,
    val subscriptionId: Int
)