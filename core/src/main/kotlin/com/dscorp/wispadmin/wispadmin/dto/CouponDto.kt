package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.Coupon
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.io.Serializable
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

@JsonIgnoreProperties(ignoreUnknown = true)
data class CouponRequestDto(
    @field:NotBlank(message = "El código es obligatorio")
    @field:Size(max = 100, message = "El código no puede superar 100 caracteres")
    val code: String? = null,

    @field:Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    val description: String? = null,

    @field:NotNull(message = "El descuento es obligatorio")
    @field:Min(value = 0, message = "El descuento no puede ser negativo")
    val discount: Int? = null,

    val expirationDate: Long? = null
) : Serializable

data class CouponResponseDto(
    val id: Int? = null,
    val code: String? = null,
    val description: String? = null,
    val discount: Int? = null,
    val expirationDate: Long? = null,
    val isUsed: Boolean = false
) : Serializable

fun CouponRequestDto.toEntity(): Coupon = Coupon(
    code = code,
    description = description,
    discount = discount,
    expirationDate = expirationDate
)

fun Coupon.toResponseDto(): CouponResponseDto = CouponResponseDto(
    id = id,
    code = code,
    description = description,
    discount = discount,
    expirationDate = expirationDate,
    isUsed = isUsed
)
