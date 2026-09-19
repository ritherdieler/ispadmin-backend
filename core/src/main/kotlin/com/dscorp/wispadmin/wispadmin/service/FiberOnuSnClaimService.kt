package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.transport.SerialSuffix
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FiberOnuSnClaimService(
    private val subscriptionRepository: SubscriptionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(FiberOnuSnClaimService::class.java)

    @Transactional
    fun claim(sn: String, excludingSubscriptionId: Int? = null) {
        val normalized = sn.trim()
        if (normalized.isEmpty()) return

        val holders = holders(normalized)
            .filter { it.id != excludingSubscriptionId }
            .filter { !it.fiberOnuSn.isNullOrBlank() }

        val occupied = holders.filter { it.serviceStatus != ServiceStatus.CANCELLED }
        if (occupied.isNotEmpty()) {
            val detail = occupied.joinToString(", ") { holder ->
                "${holder.serviceStatus.name} #${holder.id}"
            }
            throw IllegalStateException(
                "La ONU $normalized ya está asignada a la suscripción $detail. " +
                    "Cancele ese servicio antes de registrar otra."
            )
        }

        holders.filter { it.serviceStatus == ServiceStatus.CANCELLED }.forEach { release(it, normalized) }
    }

    private fun holders(sn: String): List<Subscription> {
        val suffix = SerialSuffix.normalizeSuffix(sn)
        return if (suffix != null) {
            subscriptionRepository.findByOnuSerialOrSuffix(sn, suffix)
        } else {
            subscriptionRepository.findByExactOnuSerial(sn)
        }
    }

    private fun release(subscription: Subscription, claimedSn: String) {
        logger.info(
            "Liberando ONU {} de la suscripción CANCELLED {}",
            subscription.fiberOnuSn,
            subscription.id,
        )
        subscription.fiberOnuSn = null
        subscription.tr069DeviceId = null
        subscriptionRepository.save(subscription)
        subscription.id?.let { eventPublisher.publishEvent(SubscriptionChangedEvent(it)) }
        logger.info("ONU {} liberada para reuso", claimedSn)
    }
}
