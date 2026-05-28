package com.dscorp.wispadmin.wispadmin.requestbody

import com.dscorp.wispadmin.wispadmin.dto.OnuDto

data class MigrationRequest(
    val onu: OnuDto,
    val planId: Int,
    var subscriptionId: Int,
    val price: Double?,
    val notes: String?
)