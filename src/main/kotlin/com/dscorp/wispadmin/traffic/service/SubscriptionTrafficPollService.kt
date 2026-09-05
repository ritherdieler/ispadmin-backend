package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.RouterOsTrafficCounterParser
import com.dscorp.wispadmin.routeros.RouterOsUptimeParser
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficPollResultDto
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.entity.TrafficCounterState
import com.dscorp.wispadmin.traffic.entity.TrafficRouter
import com.dscorp.wispadmin.traffic.entity.TrafficSampleStatus
import com.dscorp.wispadmin.traffic.entity.TrafficSourceRun
import com.dscorp.wispadmin.traffic.entity.TrafficSourceRunStatus
import com.dscorp.wispadmin.events.EventBusPort
import com.dscorp.wispadmin.events.NoOpEventBus
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryPort
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryTarget
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficCounterStateRepository
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Service
open class SubscriptionTrafficPollService(
    private val routerRepository: TrafficRouterRepository,
    private val directory: TrafficDirectoryPort,
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val counterStateRepository: TrafficCounterStateRepository,
    private val sourceRunRepository: TrafficSourceRunRepository,
    @Qualifier("trafficPollMikrotikClient")
    private val mikrotikClient: MikrotikClient,
    private val routerOsClientProperties: RouterOsClientProperties,
    private val trafficProperties: TrafficProperties,
    private val eventBus: EventBusPort = NoOpEventBus(),
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficPollService::class.java)
        private val running = AtomicBoolean(false)
        private const val PATH_QUEUE_SIMPLE = "/queue/simple"
        private val QUEUE_PROPLIST = listOf(".id", "target", "name", "bytes", "rate", "packets", "max-limit")
    }

    @Transactional
    open fun pollTraffic(): SubscriptionTrafficPollResultDto {
        if (!trafficProperties.poll.enabled) {
            return SubscriptionTrafficPollResultDto(skippedReason = "traffic.poll.enabled=false")
        }
        if (!running.compareAndSet(false, true)) {
            return SubscriptionTrafficPollResultDto(skippedReason = "poll already running")
        }
        val started = System.currentTimeMillis()
        return try {
            pollTrafficInternal(started)
        } finally {
            running.set(false)
        }
    }

    private fun pollTrafficInternal(started: Long): SubscriptionTrafficPollResultDto {
        val routers = routerRepository.findByEnabledTrue()
        if (routers.isEmpty()) {
            return SubscriptionTrafficPollResultDto(
                skippedReason = "no routers to poll",
                durationMs = System.currentTimeMillis() - started,
            )
        }
        val targets = loadDirectory()
        val bucketStart = truncateBucket(LocalDateTime.now(), trafficProperties.poll.bucketMinutes)
        val executor = Executors.newFixedThreadPool(
            trafficProperties.poll.maxParallelRouters.coerceIn(1, routers.size.coerceAtLeast(1)),
        )
        val outcomes = try {
            routers.map { router ->
                executor.submit<DevicePollOutcome> {
                    try {
                        DevicePollOutcome(pollRouter(router, targets, bucketStart), null)
                    } catch (ex: Exception) {
                        logger.warn("Traffic poll failed for router {}: {}", router.id, ex.message)
                        DevicePollOutcome(null, ex.message)
                    }
                }
            }.map { it.get() }
        } finally {
            executor.shutdown()
        }
        return SubscriptionTrafficPollResultDto(
            devicesPolled = outcomes.count { it.result != null },
            subscriptionsMatched = outcomes.sumOf { it.result?.matched ?: 0 },
            samplesWritten = outcomes.sumOf { it.result?.written ?: 0 },
            error = outcomes.firstNotNullOfOrNull { it.error },
            durationMs = System.currentTimeMillis() - started,
        )
    }

    private fun loadDirectory(): List<TrafficDirectoryTarget> = try {
        directory.list()
    } catch (ex: Exception) {
        logger.warn("Traffic directory unavailable; poll continues by IP: {}", ex.message)
        emptyList()
    }

    private data class DevicePollResult(val matched: Int, val written: Int)
    private data class DevicePollOutcome(val result: DevicePollResult?, val error: String?)

    private fun pollRouter(
        router: TrafficRouter,
        targets: List<TrafficDirectoryTarget>,
        bucketStart: LocalDateTime,
    ): DevicePollResult {
        val labeledForRouter = targets.filter { it.routerHint == null || it.routerHint == router.id }
        val byIp = labeledForRouter.associateBy { it.ip }
        val runStarted = LocalDateTime.now()
        val run = sourceRunRepository.save(
            TrafficSourceRun(
                hostDeviceId = router.id,
                startedAt = runStarted,
                expectedCount = labeledForRouter.size.coerceAtLeast(1),
            ),
        )
        val deviceRef = MikrotikDeviceRef(
            id = router.id.toString(),
            host = router.host,
            port = routerOsClientProperties.classic.port,
            username = router.username,
            password = router.password,
        )
        logger.info("Traffic poll connecting to {} ({})", router.name, router.host)
        var matched = 0
        var written = 0
        var missing = 0
        var invalid = 0
        try {
            mikrotikClient.withSession(deviceRef) { session ->
                val queues = session.print(PATH_QUEUE_SIMPLE, proplist = QUEUE_PROPLIST)
                val resourceRow = session.print("/system/resource").firstOrNull()
                val uptimeSeconds = RouterOsUptimeParser.parseSeconds(resourceRow?.get("uptime"))
                val queuesByIp = queues.mapNotNull { row ->
                    val ip = RouterOsTrafficCounterParser.normalizeTarget(row["target"]) ?: return@mapNotNull null
                    ip to row
                }.toMap()
                queuesByIp.forEach { (ip, queue) ->
                    try {
                        persistQueueSample(
                            ip = ip,
                            queue = queue,
                            router = router,
                            byIp = byIp,
                            bucketStart = bucketStart,
                            runId = run.id,
                            uptimeSeconds = uptimeSeconds,
                        ).also { persisted ->
                            matched++
                            written += persisted.written
                            invalid += persisted.invalid
                        }
                    } catch (ex: Exception) {
                        matched++
                        invalid++
                        logger.warn("Traffic sample persist failed ip={}: {}", ip, ex.message)
                    }
                }
                labeledForRouter.filter { it.ip !in queuesByIp }.forEach { target ->
                    missing++
                    sampleRepository.save(
                        upsertObservation(target.ip, target, router.id, bucketStart, run.id, null, TrafficSampleStatus.MISSING, "QUEUE_NOT_FOUND"),
                    )
                }
            }
            completeRun(run, TrafficSourceRunStatus.OK, matched, written, missing, invalid, null)
        } catch (ex: Exception) {
            completeRun(run, TrafficSourceRunStatus.FAILED, matched, written, missing, invalid, ex.message)
            throw ex
        }
        return DevicePollResult(matched = matched, written = written)
    }

    private data class QueuePersistResult(val written: Int, val invalid: Int)

    private fun persistQueueSample(
        ip: String,
        queue: Map<String, String>,
        router: TrafficRouter,
        byIp: Map<String, TrafficDirectoryTarget>,
        bucketStart: LocalDateTime,
        runId: Long?,
        uptimeSeconds: Long?,
    ): QueuePersistResult {
        val target = byIp[ip]
        val bytes = RouterOsTrafficCounterParser.parseUpDown(queue["bytes"])
        if (bytes == null) {
            sampleRepository.save(
                upsertObservation(ip, target, router.id, bucketStart, runId, queue, TrafficSampleStatus.INVALID, "INVALID_COUNTER"),
            )
            return QueuePersistResult(written = 0, invalid = 1)
        }
        val uploadBytes = bytes.first
        val downloadBytes = bytes.second
        val state = counterStateRepository.findById(ip).orElse(null)
        val counterReset = detectCounterReset(state?.lastRouterUptimeSeconds, uptimeSeconds) ||
            (state != null && (downloadBytes < state.lastRxBytes || uploadBytes < state.lastTxBytes))
        if (state == null) {
            sampleRepository.save(
                upsertObservation(ip, target, router.id, bucketStart, runId, queue, TrafficSampleStatus.BASELINE, null),
            )
            counterStateRepository.save(
                TrafficCounterState(
                    clientIp = ip,
                    subscriptionId = target?.subscriptionId,
                    hostDeviceId = router.id,
                    lastRxBytes = downloadBytes,
                    lastTxBytes = uploadBytes,
                    lastRouterUptimeSeconds = uptimeSeconds,
                    lastPolledAt = LocalDateTime.now(),
                ),
            )
            return QueuePersistResult(written = 1, invalid = 0)
        }
        val collectedAt = LocalDateTime.now()
        val intervalSeconds = state.lastPolledAt?.let { ChronoUnit.SECONDS.between(it, collectedAt).coerceAtLeast(1) }
            ?: (trafficProperties.poll.bucketMinutes * 60L)
        val status = when {
            counterReset -> TrafficSampleStatus.RESET
            intervalSeconds > trafficProperties.poll.bucketMinutes * 60L * 3L -> TrafficSampleStatus.STALE
            else -> TrafficSampleStatus.OK
        }
        val txDelta = if (status == TrafficSampleStatus.OK) uploadBytes - state.lastTxBytes else null
        val rxDelta = if (status == TrafficSampleStatus.OK) downloadBytes - state.lastRxBytes else null
        val observation = upsertObservation(
            ip, target, router.id, bucketStart, runId, queue, status,
            if (status == TrafficSampleStatus.STALE) "POLL_INTERVAL_EXCEEDED" else null,
        )
        observation.collectedAt = collectedAt
        observation.intervalSeconds = intervalSeconds.toInt()
        observation.rxBytesDelta = rxDelta
        observation.txBytesDelta = txDelta
        observation.avgMbpsDown = rxDelta?.let { RouterOsTrafficCounterParser.bytesToMbps(it, intervalSeconds.toDouble()) }
        observation.avgMbpsUp = txDelta?.let { RouterOsTrafficCounterParser.bytesToMbps(it, intervalSeconds.toDouble()) }
        observation.counterReset = counterReset
        sampleRepository.save(observation)
        state.subscriptionId = target?.subscriptionId
        state.hostDeviceId = router.id
        state.lastRxBytes = downloadBytes
        state.lastTxBytes = uploadBytes
        state.lastRouterUptimeSeconds = uptimeSeconds
        state.lastPolledAt = collectedAt
        counterStateRepository.save(state)
        publishLatest(observation)
        return QueuePersistResult(written = 1, invalid = 0)
    }

    private fun upsertObservation(
        clientIp: String,
        target: TrafficDirectoryTarget?,
        hostDeviceId: Int,
        bucketStart: LocalDateTime,
        sourceRunId: Long?,
        queue: Map<String, String>?,
        status: TrafficSampleStatus,
        errorReason: String?,
    ): SubscriptionTrafficSample {
        val limits = RouterOsTrafficCounterParser.parseMaxLimitMbps(queue?.get("max-limit"))
        val observation = sampleRepository.findByClientIpAndBucketStart(clientIp, bucketStart)
            ?: SubscriptionTrafficSample(clientIp = clientIp, hostDeviceId = hostDeviceId, bucketStart = bucketStart)
        observation.clientIp = clientIp
        observation.subscriptionId = target?.subscriptionId
        observation.hostDeviceId = hostDeviceId
        observation.collectedAt = LocalDateTime.now()
        observation.sampleStatus = status
        observation.errorReason = errorReason
        observation.sourceRunId = sourceRunId
        observation.queueId = queue?.get(".id")
        observation.queueName = queue?.get("name")
        observation.planDownloadMbps = limits?.second ?: target?.planDownloadMbps
        observation.planUploadMbps = limits?.first ?: target?.planUploadMbps
        if (status != TrafficSampleStatus.OK) {
            observation.rxBytesDelta = null
            observation.txBytesDelta = null
            observation.avgMbpsDown = null
            observation.avgMbpsUp = null
        }
        return observation
    }

    private fun completeRun(
        run: TrafficSourceRun,
        status: TrafficSourceRunStatus,
        matched: Int,
        written: Int,
        missing: Int,
        invalid: Int,
        error: String?,
    ) {
        val completed = LocalDateTime.now()
        run.completedAt = completed
        run.status = if (status == TrafficSourceRunStatus.OK && (missing > 0 || invalid > 0)) TrafficSourceRunStatus.PARTIAL else status
        run.matchedCount = matched
        run.writtenCount = written
        run.missingCount = missing
        run.invalidCount = invalid
        run.durationMs = ChronoUnit.MILLIS.between(run.startedAt, completed)
        run.errorMessage = error?.take(500)
        sourceRunRepository.save(run)
        eventBus.publish(
            PlatformEvent(
                type = PlatformEventTypes.TRAFFIC_POLL_RUN,
                subscriptionId = null,
                occurredAt = limaInstant(completed),
                payloadJson = """{"hostDeviceId":${run.hostDeviceId},"status":"${run.status.name}","writtenCount":${run.writtenCount}}""",
            )
        )
    }

    private fun publishLatest(observation: SubscriptionTrafficSample) {
        val subscriptionId = observation.subscriptionId ?: return
        if (observation.sampleStatus != TrafficSampleStatus.OK) return
        val down = observation.avgMbpsDown
        val up = observation.avgMbpsUp
        eventBus.publish(
            PlatformEvent(
                type = PlatformEventTypes.TRAFFIC_LATEST,
                subscriptionId = subscriptionId,
                occurredAt = limaInstant(observation.collectedAt ?: LocalDateTime.now()),
                payloadJson = """{"mbpsDown":$down,"mbpsUp":$up,"sampleStatus":"${observation.sampleStatus.name}","hostDeviceId":${observation.hostDeviceId}}""",
            )
        )
    }

    private fun limaInstant(value: LocalDateTime): Instant =
        value.atZone(ZoneId.of("America/Lima")).toInstant()

    private fun detectCounterReset(previousUptime: Long?, currentUptime: Long?): Boolean {
        if (previousUptime == null || currentUptime == null) return false
        return currentUptime < previousUptime
    }

    private fun truncateBucket(now: LocalDateTime, bucketMinutes: Int): LocalDateTime {
        val minutes = bucketMinutes.coerceAtLeast(1)
        val truncatedMinute = (now.minute / minutes) * minutes
        return now.truncatedTo(ChronoUnit.SECONDS).withMinute(truncatedMinute).withSecond(0).withNano(0)
    }
}
