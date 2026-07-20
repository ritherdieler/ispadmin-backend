package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable

/**
 * A DTO for the {@link com.dscorp.wispadmin.wispadmin.data.model.NapBox} entity
 */
data class NapBoxDto(
    val id: Int? = null,
    val code: String = "",
    val address: String = "",
    val mufaId: Int? = null,
    val latitude: Float? = null,
    val longitude: Float? = null,
    val ports_number: Int? = null,
    val oltId: Int? = null,
    val oltBoard: Int? = null,
    val oltPort: Int? = null,
    val placeName: String?=null,
    val placeId: Int?=null,
) : Serializable
