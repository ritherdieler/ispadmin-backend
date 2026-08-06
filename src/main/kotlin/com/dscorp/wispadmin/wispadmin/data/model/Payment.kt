package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.controller.toDto
import com.dscorp.wispadmin.wispadmin.dto.PaymentDto
import javax.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment",
    indexes = [
        // Índices para optimizar consultas del dashboard
        Index(name = "idx_payment_billing_date_paid", columnList = "billing_date_datetime, paid"),
        Index(name = "idx_payment_billing_date_method", columnList = "billing_date_datetime, method"),
        Index(name = "idx_payment_subscription_billing_date", columnList = "subscription_id, billing_date_datetime"),
        Index(name = "idx_payment_paid_billing_date_amount", columnList = "paid, billing_date_datetime, amountToPay"),
        Index(name = "idx_payment_method_paid", columnList = "method, paid"),
        Index(name = "idx_payment_electronic_payer", columnList = "subscription_id, electronicPayerName"),
        Index(name = "idx_payment_billing_date_subscription", columnList = "billing_date_datetime, subscription_id")
    ]
)
class Payment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Int? = null,
    var discountAmount: Double,
    var discountReason: String? = "",
    @Column(name = "billing_date_datetime")
    var billingDateDatetime: LocalDateTime = LocalDateTime.now(),
    var dueDate: LocalDateTime? = null,
    @Column(name = "payment_date_datetime")
    var paymentDateDatetime: LocalDateTime? = null,
    var method: String? = "",
    var amountPaid: Double? = null,
    var paid: Boolean,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subscription_id")
    var subscription: Subscription? = null,
    //user who made the payment
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "responsible_user_id")
    var responsible: User? = null,
    var isPaymentCommit: Boolean? = false,
    @Column(name = "payment_commitment_date_datetime")
    var paymentCommitmentDateDatetime: LocalDateTime? = null,
    var amountToPay: Double,
    var electronicPayerName: String? = null,
) {


    fun toDto(): PaymentDto = PaymentDto(
        id = id,
        discountAmount = discountAmount,
        discountReason = discountReason,
        billingDate = billingDateDatetime
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        dueDate = dueDate,
        paymentDate = paymentDateDatetime
            ?.atZone(java.time.ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli(),
        method = method,
        amountPaid = amountPaid,
        paid = paid,
        amountToPay = amountToPay,
        plan = subscription!!.plan!!.toDto(),
        responsibleName = responsible?.name,
        subscriptionId = subscription!!.id!!,
        )

    fun toPayerFinderResultDto()  = PayerFinderResultDto(
        subscriptionId = subscription!!.id!!,
        subscriptionName = subscription!!.getFullName(),
        electronicPayerName = electronicPayerName!!,
        paymentMethod = method!!,
        paymentDate = paymentDateDatetime
            ?.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            ?: "",
        amountPaid = amountPaid!!
    )


}

data class PayerFinderResultDto(
    val subscriptionId:Int,
    val subscriptionName: String,
    val electronicPayerName: String,
    val paymentMethod: String,
    val paymentDate: String,
    val amountPaid: Double,
    )
