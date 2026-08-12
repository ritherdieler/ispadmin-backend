package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.requestbody.MultiPaymentRegisterRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentCreateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentRequest
import com.dscorp.wispadmin.wispadmin.util.toLocalDateTimeOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import javax.persistence.EntityNotFoundException
import javax.transaction.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val mikrotikPaymentReactivationHandler: MikrotikPaymentReactivationHandler,
    private val proofPathResolver: PaymentProofPathResolver,
) {

    fun resolveProofPath(proofImagePath: String?, inboundMessageId: Int?): String? {
        val rawPath = when {
            !proofImagePath.isNullOrBlank() -> proofImagePath
            inboundMessageId == null -> return null
            else -> {
                val inbound = inboundMessageRepository.findById(inboundMessageId)
                    .orElseThrow { EntityNotFoundException("Inbound message not found with id $inboundMessageId") }

                if (inbound.mediaPurgedAt != null) {
                    throw IllegalArgumentException("Inbound message media was purged")
                }
                if (inbound.mediaStoredPath.isNullOrBlank()) {
                    throw IllegalArgumentException("Inbound message has no stored media path")
                }
                inbound.mediaStoredPath
            }
        }
        return proofPathResolver.toStoredFilename(rawPath)
    }

    fun toPublicDto(payment: Payment) =
        payment.toDto(proofPathResolver.toPublicPath(payment.proofImagePath))

    @Transactional
    fun createPaidPayment(request: PaymentCreateRequest): Payment {
        val subscription = subscriptionRepository.findById(request.subscriptionId)
            .orElseThrow { EntityNotFoundException("Subscription not found with id ${request.subscriptionId}") }

        val discountAmount = request.discountAmount ?: 0.0
        if (request.amountPaid <= 0) {
            throw IllegalArgumentException("El monto pagado debe ser mayor a 0")
        }

        val amountToPay = request.amountPaid + discountAmount
        if (discountAmount > amountToPay) {
            throw Exception("El descuento no puede ser mayor al monto a pagar")
        }

        val now = LocalDateTime.now()
        val payment = Payment(
            discountAmount = discountAmount,
            discountReason = request.discountReason,
            billingDateDatetime = request.billingDate?.toLocalDateTimeOrNull() ?: now,
            paid = true,
            amountToPay = amountToPay,
            amountPaid = request.amountPaid,
            method = request.method,
            subscription = subscription,
            responsible = userRepository.getReferenceById(request.responsibleId),
            electronicPayerName = request.electronicPayerName,
            paymentDateDatetime = request.paymentDate?.toLocalDateTimeOrNull() ?: now,
        )

        val saved = paymentRepository.save(payment)
        maybeReactivate(saved)
        return saved
    }

    @Transactional
    fun registerPayment(request: PaymentRequest): Payment {
        val paymentId = request.id
            ?: throw IllegalArgumentException("Payment id is required")
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { EntityNotFoundException("Payment not found with id $paymentId") }

        if (payment.paid) {
            throw IllegalArgumentException("Payment is already paid")
        }
        if (request.discountAmount > payment.amountToPay) {
            throw Exception("El descuento no puede ser mayor al monto a pagar")
        }

        val proofPath = resolveProofPath(request.proofImagePath, request.inboundMessageId)
        liquidatePayment(
            payment = payment,
            method = request.method,
            discountAmount = request.discountAmount,
            discountReason = request.discountReason,
            responsibleId = request.responsibleId,
            electronicPayerName = request.electronicPayerName,
            proofImagePath = proofPath,
        )

        paymentRepository.save(payment)
        maybeReactivate(payment)
        return payment
    }

    @Transactional
    fun registerPayments(request: MultiPaymentRegisterRequest): List<Payment> {
        if (request.paymentIds.isEmpty()) {
            throw IllegalArgumentException("At least one payment id is required")
        }
        if (request.paymentIds.size != request.paymentIds.distinct().size) {
            throw IllegalArgumentException("Duplicate payment ids are not allowed")
        }

        val payments = paymentRepository.findAllById(request.paymentIds)
        if (payments.size != request.paymentIds.distinct().size) {
            throw IllegalArgumentException("One or more payments were not found")
        }
        if (payments.any { it.paid }) {
            throw IllegalArgumentException("One or more payments are already paid")
        }

        val subscriptionIds = payments.mapNotNull { it.subscription?.id }.distinct()
        if (subscriptionIds.size != 1) {
            throw IllegalArgumentException("All payments must belong to the same subscription")
        }

        val proofPath = resolveProofPath(request.proofImagePath, request.inboundMessageId)
        val paymentDate = LocalDateTime.now()

        payments.forEach { payment ->
            if (request.discountAmount > payment.amountToPay) {
                throw Exception("El descuento no puede ser mayor al monto a pagar")
            }
            liquidatePayment(
                payment = payment,
                method = request.method,
                discountAmount = request.discountAmount,
                discountReason = request.discountReason,
                responsibleId = request.responsibleId,
                electronicPayerName = request.electronicPayerName,
                proofImagePath = proofPath,
                paymentDate = paymentDate,
            )
        }

        val saved = paymentRepository.saveAll(payments)
        maybeReactivate(payments.first())
        return saved
    }

    private fun liquidatePayment(
        payment: Payment,
        method: String,
        discountAmount: Double,
        discountReason: String?,
        responsibleId: Int,
        electronicPayerName: String?,
        proofImagePath: String?,
        paymentDate: LocalDateTime = LocalDateTime.now(),
    ) {
        payment.apply {
            amountPaid = payment.amountToPay - discountAmount
            this.method = method
            this.discountAmount = discountAmount
            this.discountReason = discountReason
            paid = true
            paymentDateDatetime = paymentDate
            responsible = userRepository.getReferenceById(responsibleId)
            this.electronicPayerName = electronicPayerName
            this.proofImagePath = proofImagePath
        }
    }

    private fun maybeReactivate(payment: Payment) {
        if (payment.subscription?.serviceStatus == ServiceStatus.CANCELLED) {
            return
        }
        payment.subscription?.let { subscription ->
            if (isEligibleForReactivation(subscription)) {
                mikrotikPaymentReactivationHandler.reactivateFromDebtorsList(subscription.toDto())
            }
        }
    }

    private fun isEligibleForReactivation(subscription: Subscription): Boolean {
        val pendingPayments = paymentRepository.findPendingPaymentsBySubscriptionId(subscription.id!!)
        return when {
            pendingPayments >= 2 -> false
            pendingPayments == 1 -> true
            pendingPayments == 0 -> true
            else -> false
        }
    }
}
