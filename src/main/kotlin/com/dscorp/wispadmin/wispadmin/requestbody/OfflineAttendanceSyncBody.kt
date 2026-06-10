package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class OfflineAttendanceSyncBody @JsonCreator constructor(
    @JsonProperty("offlineId") val offlineId: String,
    @JsonProperty("userId") val userId: Int,
    @JsonProperty("action") val action: VerifyFaceBody.Action,
    @JsonProperty("occurredAtMillis") val occurredAtMillis: Long,
    @JsonProperty("score") val score: Double? = null,
    @JsonProperty("faceDataId") val faceDataId: Int? = null
)
