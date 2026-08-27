package com.dscorp.wispadmin.oltgateway.snmp

import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs per-port optical fetches with optional parallelism and per-port failure isolation.
 */
object OpticalPortWalkRunner {

    private val logger = LoggerFactory.getLogger(OpticalPortWalkRunner::class.java)

    fun <T> runAll(
        ports: List<GponFsp>,
        parallelism: Int,
        fetch: (GponFsp) -> List<T>
    ): List<T> {
        if (ports.isEmpty()) return emptyList()
        val failures = AtomicInteger(0)
        val runOne: (GponFsp) -> List<T> = { port ->
            try {
                fetch(port)
            } catch (ex: Exception) {
                failures.incrementAndGet()
                logger.warn(
                    "SNMP optical walk failed slot={} port={}: {}",
                    port.slot,
                    port.port,
                    ex.message
                )
                emptyList()
            }
        }
        val results = if (parallelism <= 1 || ports.size == 1) {
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
        if (results.isEmpty() && failures.get() == ports.size) {
            throw IOException("all ${ports.size} optical port walks failed")
        }
        return results
    }
}
