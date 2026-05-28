package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class VerifyFaceBody @JsonCreator constructor(
    @JsonProperty("descriptor") val descriptor: List<Double> = emptyList(),
    @JsonProperty("action") val action: Action = Action.CHECK_IN
) {
    enum class Action {
        CHECK_IN,
        CHECK_OUT
    }
}
