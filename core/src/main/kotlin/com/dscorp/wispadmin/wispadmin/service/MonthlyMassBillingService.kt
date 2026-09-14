package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionActionType
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionLog
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth

@Service
class MonthlyMassBillingService(
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val subscriptionLogRepository: SubscriptionLogRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class SubscriptionProcessResult(
        val invoiceCreated: Boolean = false,
        val cancelledByUnpaid: Boolean = false,
        val skippedDuplicate: Boolean = false,
        val skippedNoPlan: Boolean = false,
    )

    data class MassBillingResult(
        val processedCount: Int = 0,
        val invoicesCreated: Int = 0,
        val cancelledCount: Int = 0,
        val skippedDuplicateCount: Int = 0,
        val skippedNoPlanCount: Int = 0,
    )

    fun billingWindow(yearMonth: YearMonth): Pair<LocalDateTime, LocalDateTime> {
        val billingDate = yearMonth.atEndOfMonth().atStartOfDay()
        val windowEnd = billingDate.plusMonths(1)
        return billingDate to windowEnd
    }

    fun dueDate(yearMonth: YearMonth): LocalDateTime = yearMonth.atDay(15).atStartOfDay()

    @Transactional
    fun generateMonthlyInvoices(closedMonth: YearMonth): MassBillingResult {
        val subscriptionIds = subscriptionRepository.findBillableSubscriptionIdsForMassBilling()
        var result = MassBillingResult()
        for (subscriptionId in subscriptionIds) {
            val subscription = subscriptionRepository.findWithPlanByIdForMassBilling(subscriptionId).orElse(null)
                ?: continue
            val step = processSubscription(subscription, closedMonth)
            result = result.copy(processedCount = result.processedCount + 1)
            when {
                step.invoiceCreated -> result = result.copy(invoicesCreated = result.invoicesCreated + 1)
                step.cancelledByUnpaid -> result = result.copy(cancelledCount = result.cancelledCount + 1)
                step.skippedDuplicate -> result = result.copy(skippedDuplicateCount = result.skippedDuplicateCount + 1)
                step.skippedNoPlan -> result = result.copy(skippedNoPlanCount = result.skippedNoPlanCount + 1)
            }
        }
        log.info(
            "Monthly mass billing for {} processed={} invoices={} cancelled={} skippedDuplicate={}",
            closedMonth,
            result.processedCount,
            result.invoicesCreated,
            result.cancelledCount,
            result.skippedDuplicateCount,
        )
        return result
    }

    fun processSubscription(subscription: Subscription, closedMonth: YearMonth): SubscriptionProcessResult {
        val plan = subscription.plan ?: return SubscriptionProcessResult(skippedNoPlan = true)
        val planPrice = plan.price ?: return SubscriptionProcessResult(skippedNoPlan = true)
        val subscriptionId = subscription.id ?: return SubscriptionProcessResult(skippedNoPlan = true)

        val pending = paymentRepository.findPendingPaymentsBySubscriptionId(subscriptionId)
        if (pending >= 2) {
            cancelForUnpaidInvoices(subscription, subscriptionId, plan.id, plan.name, planPrice)
            return SubscriptionProcessResult(cancelledByUnpaid = true)
        }

        val (billingStart, billingEnd) = billingWindow(closedMonth)
        val existsInWindow =
            paymentRepository.existsBySubscriptionIdAndBillingDateDatetimeGreaterThanEqualAndBillingDateDatetimeLessThan(
                subscriptionId,
                billingStart,
                billingEnd,
            )
        if (existsInWindow) {
            return SubscriptionProcessResult(skippedDuplicate = true)
        }

        paymentRepository.save(
            Payment(
                discountAmount = 0.0,
                discountReason = null,
                billingDateDatetime = billingStart,
                dueDate = dueDate(closedMonth),
                method = null,
                amountPaid = 0.0,
                paid = false,
                subscription = subscription,
                responsible = null,
                isPaymentCommit = false,
                amountToPay = planPrice,
                electronicPayerName = null,
            )
        )
        subscriptionRepository.applyMassBillingInvoiceSubscriptionState(subscriptionId)
        return SubscriptionProcessResult(invoiceCreated = true)
    }

    private fun cancelForUnpaidInvoices(
        subscription: Subscription,
        subscriptionId: Int,
        planId: Int?,
        planName: String?,
        planPrice: Double?,
    ) {
        val cancellationAt = LocalDateTime.now()
        subscriptionRepository.applyMassBillingCancellation(subscriptionId, cancellationAt)
        subscriptionLogRepository.save(
            SubscriptionLog(
                subscription = subscription,
                actionType = SubscriptionActionType.CANCELED_BY_STORED_PROCEDURE,
                planId = planId,
                planName = planName,
                planPrice = planPrice,
                responsibleId = "store_procedure",
            )
        )
    }
}
