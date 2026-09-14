package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class IdentifyFaceBody @JsonCreator constructor(
    @JsonProperty("descriptor") val descriptor: List<Double> = emptyList()
)
