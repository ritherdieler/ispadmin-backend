package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Runs post-install TR-069 off the HTTP thread and serializes work per subscription.
 */
@Service
class Tr069AsyncApplicator(
    private val provisioner: Tr069PostInstallProvisioner,
    private val repository: SubscriptionRepository,
    @Qualifier("tr069TaskExecutor") private val executor: Executor,
) {
    private val log = LoggerFactory.getLogger(Tr069AsyncApplicator::class.java)
    private val inFlight = ConcurrentHashMap.newKeySet<Int>()

    fun schedule(dto: SubscriptionDto, request: SubscriptionRequest) {
        val subscriptionId = dto.id ?: return
        if (!inFlight.add(subscriptionId)) {
            log.info("TR-069 ya en curso para suscripción {}; se omite schedule", subscriptionId)
            return
        }
        executor.execute {
            try {
                provisioner.apply(dto, request)
            } catch (ex: Exception) {
                log.warn(
                    "TR-069 async falló para suscripción {}: {}",
                    subscriptionId,
                    ex.message,
                )
            } finally {
                inFlight.remove(subscriptionId)
            }
        }
    }

    /**
     * Runs apply on the calling thread unless another apply is already in flight for the same id.
     * Used by explicit retry / reconciliation.
     */
    fun applyExclusive(dto: SubscriptionDto, request: SubscriptionRequest): SubscriptionDto {
        val subscriptionId = dto.id
            ?: return provisioner.apply(dto, request)
        if (!inFlight.add(subscriptionId)) {
            log.info(
                "TR-069 ya en curso para suscripción {}; se devuelve estado actual",
                subscriptionId,
            )
            return repository.findById(subscriptionId).map { it.toDto() }.orElse(dto)
        }
        return try {
            provisioner.apply(dto, request)
        } finally {
            inFlight.remove(subscriptionId)
        }
    }

    fun isInFlight(subscriptionId: Int): Boolean = inFlight.contains(subscriptionId)
}
