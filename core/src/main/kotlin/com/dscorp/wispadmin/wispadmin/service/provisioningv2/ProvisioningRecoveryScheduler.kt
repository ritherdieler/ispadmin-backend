package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.config.CorrelationIdFilter
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Clock

/** Runs only operations made due by the durable journal; a single failure cannot starve later work. */
class ProvisioningRecoveryScheduler(
    private val journal: ProvisioningJournal,
    private val executor: ProvisioningExecutor,
    private val clock: Clock,
    private val environment: String,
    private val statuses: ProvisioningSubscriptionStatusProjector? = null,
    private val heartbeat: ProvisioningWorkerHeartbeat? = null,
    private val workers: java.util.concurrent.ExecutorService? = null,
) {
    fun recover() {
        heartbeat?.beat(clock.instant())
        val due = journal.due(environment, clock.instant())
        val pool = workers
        if (pool == null) {
            due.forEach(::advanceWithContext)
            return
        }
        pool.invokeAll(due.map { operationId -> java.util.concurrent.Callable { advanceWithContext(operationId) } })
    }

    private fun advanceWithContext(operationId: String) {
        MDC.put(CorrelationIdFilter.OPERATION_MDC_KEY, operationId)
        MDC.put(CorrelationIdFilter.MDC_KEY, operationId)
        try {
            runCatching {
                executor.advance(environment, operationId)
                statuses?.project(environment, operationId)
            }.onFailure { logger.warn("No se pudo reanudar aprovisionamiento operationId={}", operationId) }
        } finally {
            MDC.remove(CorrelationIdFilter.OPERATION_MDC_KEY)
            MDC.remove(CorrelationIdFilter.MDC_KEY)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ProvisioningRecoveryScheduler::class.java)
    }
}
