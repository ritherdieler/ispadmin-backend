package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import org.springframework.stereotype.Service
import javax.persistence.EntityNotFoundException
import javax.transaction.Transactional
import java.time.LocalDateTime
@Service
class MikrotikService(
    private val repository: PaymentRepository,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val mikrotikPaymentReactivationHandler: MikrotikPaymentReactivationHandler,
) {

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
                mikrotikPaymentReactivationHandler.reactivateFromDebtorsList(payment.subscription!!.toDto())
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

}