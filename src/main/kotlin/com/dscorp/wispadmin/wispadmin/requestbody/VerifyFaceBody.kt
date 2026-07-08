package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class VerifyFaceBody @JsonCreator constructor(
    @JsonProperty("descriptor") val descriptor: List<Double> = emptyList(),
    @JsonProperty("action") val action: Action = Action.CHECK_IN,
    @JsonProperty("occurredAtMillis") val occurredAtMillis: Long? = null,
    @JsonProperty("attendanceStatus") val attendanceStatus: String? = null
) {
    enum class Action {
        CHECK_IN,
        CHECK_OUT
    }
}
