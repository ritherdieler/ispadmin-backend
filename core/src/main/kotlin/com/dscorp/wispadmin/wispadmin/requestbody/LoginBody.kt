package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class LoginBody @JsonCreator constructor(
    @JsonProperty("username") val username: String = "",
    @JsonProperty("password") val password: String = ""
)
