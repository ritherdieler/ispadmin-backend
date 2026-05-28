package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.controller.toDto
import com.dscorp.wispadmin.wispadmin.dto.PaymentDto
import com.dscorp.wispadmin.wispadmin.extensions.toFormattedDate
import javax.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment",
    indexes = [
        // Índices para optimizar consultas del dashboard
        Index(name = "idx_payment_billing_date_paid", columnList = "billingDate, paid"),
        Index(name = "idx_payment_billing_date_method", columnList = "billingDate, method"),
        Index(name = "idx_payment_subscription_billing_date", columnList = "subscription_id, billingDate"),
        Index(name = "idx_payment_paid_billing_date_amount", columnList = "paid, billingDate, amountToPay"),
        Index(name = "idx_payment_method_paid", columnList = "method, paid"),
        Index(name = "idx_payment_electronic_payer", columnList = "subscription_id, electronicPayerName"),
        Index(name = "idx_payment_billing_date_subscription", columnList = "billingDate, subscription_id")
    ]
)
class Payment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Int? = null,
    var discountAmount: Double,
    var discountReason: String? = "",
    var billingDate: Long = System.currentTimeMillis(),
    var dueDate: LocalDateTime? = null,
    var paymentDate: Long? = null,
    var method: String? = "",
    var amountPaid: Double? = null,
    var paid: Boolean,
    @ManyToOne @JoinColumn(name = "subscription_id")
    var subscription: Subscription? = null,
    //user who made the payment
    @ManyToOne @JoinColumn(name = "responsible_user_id")
    var responsible: User? = null,
    var isPaymentCommit: Boolean? = false,
    var paymentCommitmentDate: Long? = null,
    var amountToPay: Double,
    var electronicPayerName: String? = null,
) {


    fun toDto(): PaymentDto = PaymentDto(
        id = id,
        discountAmount = discountAmount,
        discountReason = discountReason,
        billingDate = billingDate,
        dueDate = dueDate,
        paymentDate = paymentDate,
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
        paymentDate = paymentDate?.toFormattedDate()?:"",
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
