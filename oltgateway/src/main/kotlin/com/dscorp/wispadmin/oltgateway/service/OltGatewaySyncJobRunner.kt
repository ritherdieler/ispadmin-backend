package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.dto.SyncJobStatusDto
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor

/**
 * Saca los POST de sync del hilo HTTP: llegaron a tardar 859 s bloqueando la petición.
 * Siempre responde 200 con el estado del trabajo; el resultado se consulta por `sync/status`.
 */
open class OltGatewaySyncJobRunner(
    private val inventorySyncService: OltInventorySyncService,
    private val signalPollService: OltSignalPollService,
    private val executor: Executor
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltGatewaySyncJobRunner::class.java)
        const val JOB_INVENTORY = "inventory"
        const val JOB_SNMP_INVENTORY = "snmp-inventory"
        const val JOB_SIGNAL = "signal"
    }

    open fun startInventory(): SyncJobStatusDto =
        startInventoryJob(JOB_INVENTORY) { inventorySyncService.syncInventory() }

    open fun startSnmpInventory(): SyncJobStatusDto =
        startInventoryJob(JOB_SNMP_INVENTORY) { inventorySyncService.syncInventoryFromSnmp() }

    open fun startSignal(): SyncJobStatusDto {
        val status = signalPollService.status()
        if (status.running) {
            return SyncJobStatusDto(
                job = JOB_SIGNAL,
                started = false,
                running = true,
                skippedReason = "already_running",
                lastStartedAt = status.lastStartedAt,
                lastSignalResult = status.lastResult
            )
        }
        submit(JOB_SIGNAL) { signalPollService.pollSignals() }
        return SyncJobStatusDto(
            job = JOB_SIGNAL,
            started = true,
            running = true,
            lastStartedAt = status.lastStartedAt,
            lastSignalResult = status.lastResult
        )
    }

    private fun startInventoryJob(job: String, work: () -> Any): SyncJobStatusDto {
        val status = inventorySyncService.status()
        if (status.running) {
            return SyncJobStatusDto(
                job = job,
                started = false,
                running = true,
                skippedReason = "already_running",
                lastStartedAt = status.lastStartedAt,
                lastResult = status.lastResult
            )
        }
        submit(job, work)
        return SyncJobStatusDto(
            job = job,
            started = true,
            running = true,
            lastStartedAt = status.lastStartedAt,
            lastResult = status.lastResult
        )
    }

    private fun submit(job: String, work: () -> Any) {
        executor.execute {
            try {
                work()
            } catch (ex: Exception) {
                logger.warn("OLT sync job {} failed: {}", job, ex.message)
            }
        }
    }
}
