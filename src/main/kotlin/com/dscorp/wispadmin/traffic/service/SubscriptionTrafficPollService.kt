package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.netdiag.service.RouterOsUptimeParser
import com.dscorp.wispadmin.routeros.RouterOsTrafficCounterParser
import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficPollResultDto
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficCounterState
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficCounterStateRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
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

@Service
open class SubscriptionTrafficPollService(
    private val subscriptionRepository: SubscriptionRepository,
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val counterStateRepository: SubscriptionTrafficCounterStateRepository,
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
        val intervalSeconds = trafficProperties.poll.bucketMinutes * 60.0
        var devicesPolled = 0
        var subscriptionsMatched = 0
        var samplesWritten = 0
        var lastError: String? = null

        subscriptions.groupBy { requireNotNull(it.hostDevice).id }.forEach { (deviceId, deviceSubscriptions) ->
            val device = deviceSubscriptions.first().hostDevice ?: return@forEach
            try {
                val result = pollDevice(
                    device = device,
                    deviceSubscriptions = deviceSubscriptions,
                    bucketStart = bucketStart,
                    intervalSeconds = intervalSeconds
                )
                devicesPolled++
                subscriptionsMatched += result.matched
                samplesWritten += result.written
            } catch (ex: Exception) {
                lastError = ex.message
                logger.warn("Traffic poll failed for device {}: {}", deviceId, ex.message)
            }
        }

        return SubscriptionTrafficPollResultDto(
            devicesPolled = devicesPolled,
            subscriptionsMatched = subscriptionsMatched,
            samplesWritten = samplesWritten,
            error = lastError,
            durationMs = System.currentTimeMillis() - started
        )
    }

    private data class DevicePollResult(val matched: Int, val written: Int)

    private fun pollDevice(
        device: NetworkDevice,
        deviceSubscriptions: List<Subscription>,
        bucketStart: LocalDateTime,
        intervalSeconds: Double
    ): DevicePollResult {
        val deviceRef = MikrotikDeviceRefMapper.toDeviceRef(
            device,
            routerOsClientProperties.classic.port,
            applyDevConnectionOverride = false
        )
        logger.info("Traffic poll connecting to {} ({})", device.name, device.ipAddress)
        var matched = 0
        var written = 0

        mikrotikClient.withSession(deviceRef) { session ->
            val queues = session.print(PATH_QUEUE_SIMPLE, proplist = QUEUE_PROPLIST)
            val resourceRow = session.print("/system/resource").firstOrNull()
            val uptimeSeconds = RouterOsUptimeParser.parseSeconds(resourceRow?.get("uptime"))
            val queuesByIp = queues.mapNotNull { row ->
                val ip = RouterOsTrafficCounterParser.normalizeTarget(row["target"]) ?: return@mapNotNull null
                ip to row
            }.toMap()

            deviceSubscriptions.forEach { subscription ->
                val ip = subscription.ip?.trim().orEmpty()
                if (ip.isEmpty()) return@forEach
                val queue = queuesByIp[ip]
                if (queue == null) {
                    logger.debug("No queue for subscription {} ip {}", subscription.id, ip)
                    return@forEach
                }
                matched++
                val bytes = RouterOsTrafficCounterParser.parseUpDown(queue["bytes"])
                val rate = RouterOsTrafficCounterParser.parseUpDown(queue["rate"])
                if (bytes == null) return@forEach

                val uploadBytes = bytes.first
                val downloadBytes = bytes.second
                val state = counterStateRepository.findById(subscription.id!!).orElse(null)
                val counterReset = detectCounterReset(state?.lastRouterUptimeSeconds, uptimeSeconds)

                if (state == null) {
                    counterStateRepository.save(
                        SubscriptionTrafficCounterState(
                            subscriptionId = subscription.id!!,
                            hostDeviceId = device.id,
                            lastRxBytes = downloadBytes,
                            lastTxBytes = uploadBytes,
                            lastRouterUptimeSeconds = uptimeSeconds,
                            lastPolledAt = LocalDateTime.now()
                        )
                    )
                    return@forEach
                }

                val txDelta = RouterOsTrafficCounterParser.computeDelta(state.lastTxBytes, uploadBytes, counterReset)
                val rxDelta = RouterOsTrafficCounterParser.computeDelta(state.lastRxBytes, downloadBytes, counterReset)

                val avgMbpsUp = rate?.first?.let { RouterOsTrafficCounterParser.bytesToMbps(it, 1.0) }
                    ?: RouterOsTrafficCounterParser.bytesToMbps(txDelta, intervalSeconds)
                val avgMbpsDown = rate?.second?.let { RouterOsTrafficCounterParser.bytesToMbps(it, 1.0) }
                    ?: RouterOsTrafficCounterParser.bytesToMbps(rxDelta, intervalSeconds)

                val existing = sampleRepository.findBySubscriptionIdAndBucketStart(subscription.id!!, bucketStart)

                if (existing == null) {
                    sampleRepository.save(
                        SubscriptionTrafficSample(
                            subscriptionId = subscription.id!!,
                            hostDeviceId = device.id,
                            bucketStart = bucketStart,
                            rxBytesDelta = rxDelta,
                            txBytesDelta = txDelta,
                            avgMbpsDown = avgMbpsDown,
                            avgMbpsUp = avgMbpsUp,
                            counterReset = counterReset
                        )
                    )
                    written++
                } else {
                    existing.rxBytesDelta = rxDelta
                    existing.txBytesDelta = txDelta
                    existing.avgMbpsDown = avgMbpsDown
                    existing.avgMbpsUp = avgMbpsUp
                    existing.counterReset = counterReset
                    sampleRepository.save(existing)
                }

                val nextState = state
                nextState.hostDeviceId = device.id
                nextState.lastRxBytes = downloadBytes
                nextState.lastTxBytes = uploadBytes
                nextState.lastRouterUptimeSeconds = uptimeSeconds
                nextState.lastPolledAt = LocalDateTime.now()
                counterStateRepository.save(nextState)
            }
        }
        return DevicePollResult(matched = matched, written = written)
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
