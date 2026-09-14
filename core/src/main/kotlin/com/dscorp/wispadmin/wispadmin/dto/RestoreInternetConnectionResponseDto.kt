package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable

/**
 * DTO para la respuesta de restablecimiento de conexión a internet
 */
data class RestoreInternetConnectionResponseDto(
    val message: String,
    val subscriptionId: Int
) : Serializable



