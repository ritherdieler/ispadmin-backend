package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable

/**
 * DTO para la respuesta de validación de reactivación
 */
data class ReactivationValidationResponseDto(
    val canReactivate: Boolean,
    val borneNumber: String? = null,
    val originalBorne: String? = null,
    val napBoxId: Int? = null,
    val napBoxCode: String? = null,
    val availableBornes: List<String>? = null,
    val message: String? = null
) : Serializable
