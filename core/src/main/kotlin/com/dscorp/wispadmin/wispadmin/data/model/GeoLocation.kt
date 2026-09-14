package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.GeoLocationDto
import com.fasterxml.jackson.annotation.JsonProperty

data class GeoLocation(
    @JsonProperty("latitude")
    val latitude: Double,
    @JsonProperty("longitude")
    val longitude: Double
) {
    fun toDto() = GeoLocationDto(latitude, longitude)
}
