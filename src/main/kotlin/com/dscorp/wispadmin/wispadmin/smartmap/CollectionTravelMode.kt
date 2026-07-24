package com.dscorp.wispadmin.wispadmin.smartmap

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

enum class CollectionTravelMode {
    VEHICLE,
    WALKING,
    ;

    fun toApiValue(): String = name.lowercase()

    companion object {
        fun fromApiValue(raw: String?): CollectionTravelMode {
            val normalized = raw?.trim()?.uppercase()?.replace('-', '_') ?: return VEHICLE
            return when (normalized) {
                "VEHICLE", "DRIVING", "CAR" -> VEHICLE
                "WALKING", "WALK", "FOOT", "ON_FOOT" -> WALKING
                else -> throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "travelMode invalido: use vehicle o walking",
                )
            }
        }
    }
}
