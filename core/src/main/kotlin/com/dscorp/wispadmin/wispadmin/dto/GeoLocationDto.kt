package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable

data class GeoLocationDto(
    val latitude: Double,
    val longitude: Double
) : Serializable
