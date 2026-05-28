package com.dscorp.wispadmin.wispadmin.response

data class UnconfirmedOnuResponse(
    val response: List<Response>,
    val status: Boolean
){
    constructor() : this(emptyList(), false)
}