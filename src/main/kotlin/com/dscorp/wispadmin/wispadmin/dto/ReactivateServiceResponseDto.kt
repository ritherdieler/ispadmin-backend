package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable

/**
 * DTO para la respuesta de reactivación de servicio
 */
data class ReactivateServiceResponseDto(
    val message: String,
    val subscriptionId: Int
) : Serializable
