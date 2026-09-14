package com.dscorp.wispadmin.wispadmin.requestbody

data class AssistanceTicketRequest(
    val phone: String = "",
    val category: String,
    val description: String,
    val subscriptionId: Int? = null,
    val customerName: String = "",
    val placeName: String? = null,
    val externalCustomerName: String? = null,
)
