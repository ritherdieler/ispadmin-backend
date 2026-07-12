package com.dscorp.wispadmin.observability.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RumIngestBatchRequest(
    val vitals: List<RumVitalRequest> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RumVitalRequest(
    val metricName: String? = null,
    val value: Double? = null,
    val rating: String? = null,
    val page: String? = null,
    val navigationType: String? = null,
    val timestamp: Long? = null
)

data class RumIngestResponse(
    val accepted: Int,
    val rejected: Int
)
