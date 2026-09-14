package com.dscorp.wispadmin.wispadmin.response

data class OnuActionResponseDto(
    val status: Boolean = true,
    val response_code: String = "200",
    val message: String? = null,
    val unique_external_id: String? = null
)
