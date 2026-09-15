package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.servicehealth.service.HealthSnapshotConsumer
import com.dscorp.wispadmin.wispadmin.service.CpeProvisioningEventConsumer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Polls Redis Streams even when [gigafiber.scheduling.enabled] is false.
 * Prestaging keeps billing/OLT jobs off; 360 still needs [HealthSnapshotConsumer].
 *
 * One thread per consumer group: a slow Inform batch on `snapshot-core` must not
 * stall `cpe-provision-core`, and vice versa.
 */
@Configuration
@ConditionalOnProperty(prefix = "gigafiber.redis", name = ["enabled"], havingValue = "true")
class RedisStreamConsumerRuntime(
    private val snapshot: ObjectProvider<HealthSnapshotConsumer>,
    private val cpe: ObjectProvider<CpeProvisioningEventConsumer>,
    @Value("\${gigafiber.redis.consumer-interval-ms:1000}") private val intervalMs: Long,
) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executors = mutableListOf<ScheduledExecutorService>()
    @Volatile private var running = false

    private fun groups(): List<Pair<String, () -> Unit>> = listOf(
        "snapshot-core" to { snapshot.ifAvailable?.poll(); Unit },
        "cpe-provision-core" to { cpe.ifAvailable?.poll(); Unit },
    )

    override fun start() {
        if (running) return
        running = true
        val delay = intervalMs.coerceAtLeast(200)
        for ((group, poll) in groups()) {
            val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "gigafiber-redis-$group").apply { isDaemon = true }
            }
            executors += executor
            executor.scheduleWithFixedDelay({
                try {
                    poll()
                } catch (ex: Exception) {
                    logger.warn("Redis poll failed for group {}: {}", group, ex.message)
                }
            }, delay, delay, TimeUnit.MILLISECONDS)
        }
    }

    override fun stop() {
        running = false
        executors.forEach { it.shutdownNow() }
        executors.clear()
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = Int.MAX_VALUE
}
