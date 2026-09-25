package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import org.slf4j.LoggerFactory
import java.time.Clock

/** Runs only operations made due by the durable journal; a single failure cannot starve later work. */
class ProvisioningRecoveryScheduler(
    private val journal: ProvisioningJournal,
    private val executor: ProvisioningExecutor,
    private val clock: Clock,
    private val environment: String,
    private val statuses: ProvisioningSubscriptionStatusProjector? = null,
) {
    fun recover() {
        journal.due(environment, clock.instant()).forEach { operationId ->
            runCatching {
                executor.advance(environment, operationId)
                statuses?.project(environment, operationId)
            }.onFailure { logger.warn("No se pudo reanudar aprovisionamiento operationId={}", operationId) }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ProvisioningRecoveryScheduler::class.java)
    }
}
