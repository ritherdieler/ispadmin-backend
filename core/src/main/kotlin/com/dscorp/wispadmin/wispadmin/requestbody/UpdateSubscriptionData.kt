package com.dscorp.wispadmin.wispadmin.requestbody

data class UpdateSubscriptionData(
    val subscriptionId: Int,
    val name: String,
    val lastName: String,
    val dni: String,
    val place: String,
    val address: String,
    val phone: String,
    val email: String,
    var placeId: Int?=null,
    )