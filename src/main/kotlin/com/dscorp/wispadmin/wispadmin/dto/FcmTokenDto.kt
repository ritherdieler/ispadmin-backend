package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.FcmToken
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.io.Serializable
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

@JsonIgnoreProperties(ignoreUnknown = true)
data class FcmTokenRequestDto(
    @field:NotNull(message = "El subscriptionId es obligatorio")
    val subscriptionId: Int? = null,

    @field:NotBlank(message = "El token es obligatorio")
    val token: String? = null
) : Serializable

data class FcmTokenResponseDto(
    val subscriptionId: Int? = null,
    val token: String? = null
) : Serializable

fun FcmTokenRequestDto.toEntity(): FcmToken = FcmToken(
    subscriptionId = subscriptionId!!,
    token = token!!
)

fun FcmToken.toResponseDto(): FcmTokenResponseDto = FcmTokenResponseDto(
    subscriptionId = subscriptionId,
    token = token
)
