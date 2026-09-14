package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class FaceLoginBody @JsonCreator constructor(
    @JsonProperty("descriptor")
    val descriptor: List<Double> = emptyList()
)
// cree nuevo archivo para el descriptor 15/05/2026