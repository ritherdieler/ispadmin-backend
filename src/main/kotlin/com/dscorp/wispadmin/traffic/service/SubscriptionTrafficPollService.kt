package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.netdiag.service.RouterOsUptimeParser
import com.dscorp.wispadmin.routeros.RouterOsTrafficCounterParser
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficPollResultDto
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficCounterState
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.entity.TrafficSampleStatus
import com.dscorp.wispadmin.traffic.entity.TrafficSourceRun
import com.dscorp.wispadmin.traffic.entity.TrafficSourceRunStatus
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficCounterStateRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.MikrotikDeviceRefMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

@Service
open class SubscriptionTrafficPollService(
    private val subscriptionRepository: SubscriptionRepository,
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val counterStateRepository: SubscriptionTrafficCounterStateRepository,
    private val sourceRunRepository: TrafficSourceRunRepository,
    @Qualifier("trafficPollMikrotikClient")
    private val mikrotikClient: MikrotikClient,
    private val routerOsClientProperties: RouterOsClientProperties,
    private val trafficProperties: TrafficProperties
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficPollService::class.java)
        private val running = AtomicBoolean(false)
        private const val PATH_QUEUE_SIMPLE = "/queue/simple"
        private val QUEUE_PROPLIST = listOf(".id", "target", "name", "bytes", "rate", "packets")
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
        val subscriptions = subscriptionRepository.findForTrafficPolling()
        if (subscriptions.isEmpty()) {
            return SubscriptionTrafficPollResultDto(
                skippedReason = "no subscriptions to poll",
                durationMs = System.currentTimeMillis() - started
            )
        }
        val bucketStart = truncateBucket(LocalDateTime.now(), trafficProperties.poll.bucketMinutes)
        val groups = subscriptions.groupBy { requireNotNull(it.hostDevice).id }
        val executor = Executors.newFixedThreadPool(trafficProperties.poll.maxParallelRouters.coerceIn(1, groups.size.coerceAtLeast(1)))
        val outcomes = try {
            groups.map { (deviceId, deviceSubscriptions) -> executor.submit<DevicePollOutcome> {
                val device = requireNotNull(deviceSubscriptions.first().hostDevice)
                try { DevicePollOutcome(pollDevice(device, deviceSubscriptions, bucketStart), null) }
                catch (ex: Exception) { logger.warn("Traffic poll failed for device {}: {}", deviceId, ex.message); DevicePollOutcome(null, ex.message) }
            } }.map { it.get() }
        } finally { executor.shutdown() }
        val devicesPolled = outcomes.count { it.result != null }
        val subscriptionsMatched = outcomes.sumOf { it.result?.matched ?: 0 }
        val samplesWritten = outcomes.sumOf { it.result?.written ?: 0 }
        val lastError = outcomes.firstNotNullOfOrNull { it.error }

        return SubscriptionTrafficPollResultDto(
            devicesPolled = devicesPolled,
            subscriptionsMatched = subscriptionsMatched,
            samplesWritten = samplesWritten,
            error = lastError,
            durationMs = System.currentTimeMillis() - started
        )
    }

    private data class DevicePollResult(val matched: Int, val written: Int)
    private data class DevicePollOutcome(val result: DevicePollResult?, val error: String?)

    private fun pollDevice(
        device: NetworkDevice,
        deviceSubscriptions: List<Subscription>,
        bucketStart: LocalDateTime
    ): DevicePollResult {
        val runStarted = LocalDateTime.now()
        val run = sourceRunRepository.save(TrafficSourceRun(
            hostDeviceId = device.id,
            startedAt = runStarted,
            expectedCount = deviceSubscriptions.size
        ))
        val deviceRef = MikrotikDeviceRefMapper.toDeviceRef(
            device,
            routerOsClientProperties.classic.port,
            applyDevConnectionOverride = false
        )
        logger.info("Traffic poll connecting to {} ({})", device.name, device.ipAddress)
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

                deviceSubscriptions.forEach { subscription ->
                    val subscriptionId = subscription.id ?: return@forEach
                    val ip = subscription.ip?.trim().orEmpty()
                    val queue = queuesByIp[ip]
                    if (queue == null) {
                        missing++
                        sampleRepository.save(upsertObservation(subscription, device.id, bucketStart, run.id, null, TrafficSampleStatus.MISSING, "QUEUE_NOT_FOUND"))
                        return@forEach
                    }
                    matched++
                    val bytes = RouterOsTrafficCounterParser.parseUpDown(queue["bytes"])
                    if (bytes == null) {
                        invalid++
                        sampleRepository.save(upsertObservation(subscription, device.id, bucketStart, run.id, queue, TrafficSampleStatus.INVALID, "INVALID_COUNTER"))
                        return@forEach
                    }

                    val uploadBytes = bytes.first
                    val downloadBytes = bytes.second
                    val state = counterStateRepository.findById(subscriptionId).orElse(null)
                    val counterReset = detectCounterReset(state?.lastRouterUptimeSeconds, uptimeSeconds) ||
                        (state != null && (downloadBytes < state.lastRxBytes || uploadBytes < state.lastTxBytes))

                    if (state == null) {
                        sampleRepository.save(upsertObservation(subscription, device.id, bucketStart, run.id, queue, TrafficSampleStatus.BASELINE, null))
                        counterStateRepository.save(SubscriptionTrafficCounterState(
                            subscriptionId = subscriptionId,
                            hostDeviceId = device.id,
                            lastRxBytes = downloadBytes,
                            lastTxBytes = uploadBytes,
                            lastRouterUptimeSeconds = uptimeSeconds,
                            lastPolledAt = LocalDateTime.now()
                        ))
                        written++
                        return@forEach
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
                    val avgMbpsUp = txDelta?.let { RouterOsTrafficCounterParser.bytesToMbps(it, intervalSeconds.toDouble()) }
                    val avgMbpsDown = rxDelta?.let { RouterOsTrafficCounterParser.bytesToMbps(it, intervalSeconds.toDouble()) }

                    val observation = upsertObservation(subscription, device.id, bucketStart, run.id, queue, status,
                        if (status == TrafficSampleStatus.STALE) "POLL_INTERVAL_EXCEEDED" else null)
                    observation.collectedAt = collectedAt
                    observation.intervalSeconds = intervalSeconds.toInt()
                    observation.rxBytesDelta = rxDelta
                    observation.txBytesDelta = txDelta
                    observation.avgMbpsDown = avgMbpsDown
                    observation.avgMbpsUp = avgMbpsUp
                    observation.counterReset = counterReset
                    sampleRepository.save(observation)
                    written++

                    state.hostDeviceId = device.id
                    state.lastRxBytes = downloadBytes
                    state.lastTxBytes = uploadBytes
                    state.lastRouterUptimeSeconds = uptimeSeconds
                    state.lastPolledAt = collectedAt
                    counterStateRepository.save(state)
                }
            }
            completeRun(run, TrafficSourceRunStatus.OK, matched, written, missing, invalid, null)
        } catch (ex: Exception) {
            completeRun(run, TrafficSourceRunStatus.FAILED, matched, written, missing, invalid, ex.message)
            throw ex
        }
        return DevicePollResult(matched = matched, written = written)
    }

    private fun upsertObservation(
        subscription: Subscription,
        hostDeviceId: Int,
        bucketStart: LocalDateTime,
        sourceRunId: Long?,
        queue: Map<String, String>?,
        status: TrafficSampleStatus,
        errorReason: String?
    ): SubscriptionTrafficSample {
        val subscriptionId = requireNotNull(subscription.id)
        val observation = sampleRepository.findBySubscriptionIdAndBucketStart(subscriptionId, bucketStart)
            ?: SubscriptionTrafficSample(subscriptionId = subscriptionId, hostDeviceId = hostDeviceId, bucketStart = bucketStart)
        observation.collectedAt = LocalDateTime.now()
        observation.sampleStatus = status
        observation.errorReason = errorReason
        observation.sourceRunId = sourceRunId
        observation.queueId = queue?.get(".id")
        observation.queueName = queue?.get("name")
        observation.planDownloadMbps = subscription.plan?.downloadSpeed
        observation.planUploadMbps = subscription.plan?.uploadSpeed
        if (status != TrafficSampleStatus.OK) {
            observation.rxBytesDelta = null
            observation.txBytesDelta = null
            observation.avgMbpsDown = null
            observation.avgMbpsUp = null
        }
        return observation
    }

    private fun completeRun(run: TrafficSourceRun, status: TrafficSourceRunStatus, matched: Int, written: Int, missing: Int, invalid: Int, error: String?) {
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
    }

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
