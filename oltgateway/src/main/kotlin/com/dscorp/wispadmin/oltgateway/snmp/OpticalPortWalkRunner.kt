package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.ssh.LocalCliBusPressure
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Runs per-port optical fetches with optional parallelism and per-port failure isolation.
 */
object OpticalPortWalkRunner {

    private val logger = LoggerFactory.getLogger(OpticalPortWalkRunner::class.java)

    data class Batch<T>(
        val items: List<T>,
        val portsAttempted: Int,
        val portsFailed: Int,
    )

    fun <T> runAll(
        ports: List<GponFsp>,
        parallelism: Int,
        pressureSnapshot: () -> LocalCliBusPressure = { LocalCliBusPressure.snapshot(null) },
        fetch: (GponFsp) -> List<T>
    ): Batch<T> {
        if (ports.isEmpty()) return Batch(emptyList(), 0, 0)
        val failedFirst = ConcurrentLinkedQueue<GponFsp>()
        val runOne: (GponFsp) -> List<T> = { port ->
            try {
                fetch(port)
            } catch (ex: Exception) {
                failedFirst.add(port)
                logPortFail(port, pressureSnapshot(), ex)
                emptyList()
            }
        }
        val firstPass = if (parallelism <= 1 || ports.size == 1) {
            ports.flatMap(runOne)
        } else {
            val poolSize = parallelism.coerceAtMost(ports.size)
            val executor = Executors.newFixedThreadPool(poolSize)
            try {
                ports.map { port ->
                    executor.submit<List<T>> { runOne(port) }
                }.flatMap { it.get() }
            } finally {
                executor.shutdown()
            }
        }
        val results = firstPass.toMutableList()
        val stillFailed = mutableListOf<GponFsp>()
        for (port in failedFirst) {
            try {
                results += fetch(port)
            } catch (ex: Exception) {
                stillFailed += port
                logPortFail(port, pressureSnapshot(), ex)
            }
        }
        if (results.isEmpty() && stillFailed.size == ports.size) {
            throw IOException("all ${ports.size} optical port walks failed")
        }
        return Batch(items = results, portsAttempted = ports.size, portsFailed = stillFailed.size)
    }

    private fun logPortFail(port: GponFsp, pressure: LocalCliBusPressure, ex: Exception) {
        logger.warn(
            "SNMP_OPTICAL_PORT_FAIL slot={} port={} localCliBus=true localQueueDepth={} localBusyJobType={} sshActive={} sshMax={}: {}",
            port.slot,
            port.port,
            pressure.localQueueDepth,
            pressure.localBusyJobType,
            LocalCliBusPressure.SSH_ACTIVE_NA,
            LocalCliBusPressure.SSH_MAX_NA,
            ex.message
        )
    }
}
