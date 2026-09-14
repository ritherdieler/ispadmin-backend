package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable
import java.time.LocalDateTime

/**
 * A DTO for the {@link com.dscorp.wispadmin.wispadmin.data.model.Payment} entity
 */
data class PaymentDto(
    val id: Int? = null,
    val discountAmount: Double? = null,
    val discountReason: String? = null,
    val billingDate: Long? = null,
    val dueDate: LocalDateTime? = null,
    val paymentDate: Long? = null,
    val method: String? = null,
    val amountPaid: Double? = null,
    val paid: Boolean? = null,
    val amountToPay: Double? = null,
    val plan: PlanDto,
    val responsibleName: String? = null,
    val subscriptionId: Int,
    val proofImagePath: String? = null,
) : Serializable