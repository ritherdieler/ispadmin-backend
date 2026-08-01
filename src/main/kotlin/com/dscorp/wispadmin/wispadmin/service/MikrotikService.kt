package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentRequest
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import org.springframework.stereotype.Service
import javax.persistence.EntityNotFoundException
import javax.transaction.Transactional
import java.time.LocalDateTime
@Service
class MikrotikService(
    private val repository: PaymentRepository,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository
) {


    @Transactional
    fun savePayment(newPayment: PaymentRequest): Payment {

        val payment = repository.findById(newPayment.id!!).get()

        if (newPayment.discountAmount > payment.amountToPay) throw Exception("El descuento no puede ser mayor al monto a pagar")



        payment.apply {
            amountPaid = payment.amountToPay - newPayment.discountAmount
            method = newPayment.method
            discountAmount = newPayment.discountAmount
            discountReason = newPayment.discountReason
            paid = true
            paymentDateDatetime = LocalDateTime.now()
            responsible = userRepository.getReferenceById(newPayment.responsibleId)
            electronicPayerName = newPayment.electronicPayerName
        }
        repository.save(payment)

        if (payment.subscription?.serviceStatus != ServiceStatus.CANCELLED) {
            payment.subscription?.let {
                if (isEligibleForReactivation(it)) {
                    reactivateServiceInMikrotik(it.toDto())
//                    UpdateSubscriptionStateToActive(payment)
                }
            }
        }


        return payment
    }

    private fun UpdateSubscriptionStateToActive(payment: Payment) {
        val subscription = payment.subscription?.apply {
            serviceStatus = ServiceStatus.ACTIVE
        }
        subscriptionRepository.save(subscription!!)
    }

    @Transactional
    fun savePaymentWithCard(id: Int): Payment {
        val payment = repository.findById(id).orElseThrow { EntityNotFoundException("Payment not found with id $id") }

        payment.apply {
            paid = true
            method = "card-app"
            amountPaid = amountToPay
            paymentDateDatetime = LocalDateTime.now()
        }

        repository.save(payment)
        payment.subscription?.let {
            if (isEligibleForReactivation(payment.subscription!!)) {
                reactivateServiceInMikrotik(payment.subscription!!.toDto())
            }
        }

        return payment
    }

    private fun isEligibleForReactivation(subscription: Subscription): Boolean {
        val pendingPayments = repository.findPendingPaymentsBySubscriptionId(subscription.id!!)
        return when {
            pendingPayments >= 2 -> false
            pendingPayments == 1 -> true
            pendingPayments == 0 -> true
            else -> false
        }
    }


    private fun reactivateServiceInMikrotik(subscription: SubscriptionDto) {
        subscription.hostDevice?.executeCommand { session ->
            if (subscription.ip.isValidIpAddress()) {
                session.print("/ip/firewall/address-list", mapOf("list" to "deudores", "address" to subscription.ip!!))
                    .forEach { addressEntry ->
                        addressEntry[".id"]?.let { id -> session.remove("/ip/firewall/address-list", id) }
                    }
            }
        }
    }

}