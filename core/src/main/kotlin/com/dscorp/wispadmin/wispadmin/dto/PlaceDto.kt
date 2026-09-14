package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import java.io.Serializable

/**
 * A DTO for the {@link com.dscorp.wispadmin.wispadmin.data.model.Place} entity
 */
data class PlaceDto(
    val id: Int? = null,
    val abbreviation: String? = null,
    val name: String? = null,
    val latitude: Float? = null,
    val longitude: Float? = null,
) : Serializable