package com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest

data class MoveOnuRequest(
    val subscriptionId: Int,
    val newNapBoxId: Int,
)