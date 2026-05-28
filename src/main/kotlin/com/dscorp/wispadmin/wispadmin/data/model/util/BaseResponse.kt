package com.dscorp.wispadmin.wispadmin.data.model.util

data class BaseResponse(
    var status: Int? = null,
    var data: Any? = null,
    var message: String? = null,
    var error: Any? = null
)