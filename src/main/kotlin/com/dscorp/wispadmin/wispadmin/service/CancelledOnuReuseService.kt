package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.service.onu.OnuOperationsPort
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CancelledOnuReuseService(
    private val onuService: OnuOperationsPort,
    private val subscriptionRepository: SubscriptionRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(CancelledOnuReuseService::class.java)

    fun authorizeWithCancelledReuse(request: OnuAuthorizationRequest) {
        try {
            onuService.authorizeOnuInSmartOltWidthPostMethod(request)
        } catch (ex: Exception) {
            if (!isSnAlreadyExistsError(ex)) throw ex

            val cancelled = findCancelledSubscriptionByOnuSn(request.sn) ?: throw ex

            releaseOnuFromCancelledSubscription(cancelled, request.sn)
            onuService.authorizeOnuInSmartOltWidthPostMethod(request)
        }
    }

    private fun isSnAlreadyExistsError(ex: Throwable): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            val message = current.message?.lowercase()
            if (message != null &&
                (message.contains("sn_already_exists") ||
                    message.contains("already exists on this olt") ||
                    message.contains("already authorized"))
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun findCancelledSubscriptionByOnuSn(sn: String): Subscription? {
        val suffix = if (sn.length >= SUFFIX_LENGTH) sn.takeLast(SUFFIX_LENGTH) else sn
        val matches = subscriptionRepository.findCancelledByFiberOnuSn(sn, suffix)
        if (matches.isEmpty()) return null
        if (matches.size > 1) {
            logger.warn(
                "Multiples suscripciones CANCELLED coinciden con el SN $sn (${matches.size}); " +
                    "se usa la de cancelacion mas reciente"
            )
        }
        return matches.maxByOrNull { it.cancellationDateDatetime ?: LocalDateTime.MIN }
    }

    private fun releaseOnuFromCancelledSubscription(subscription: Subscription, oltSn: String) {
        logger.info("Liberando ONU $oltSn de la suscripcion CANCELLED ${subscription.id} para reuso")
        onuService.deleteOnuBySn(oltSn)
        subscription.fiberOnu = null
        subscriptionRepository.save(subscription)
        subscription.id?.let { eventPublisher.publishEvent(SubscriptionChangedEvent(it)) }
    }

    companion object {
        private const val SUFFIX_LENGTH = 8
    }
}
