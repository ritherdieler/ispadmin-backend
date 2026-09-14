package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation


data class UpdateSubscriptionDataBody(
    val subscriptionId: Int,
    val firstName: String,
    val lastName: String,
    val dni: String,
    val address: String,
    val phone: String,
    val placeId: Int,
    val location: GeoLocation

)