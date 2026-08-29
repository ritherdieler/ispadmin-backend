package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.RouterOsTrafficCounterParser
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficLiveTickDto
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

data class SubscriptionTrafficLiveTickState(
    val lastRxBytes: Long? = null,
    val lastTxBytes: Long? = null,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L
)

data class SubscriptionTrafficLiveTickBuildResult(
    val tick: SubscriptionTrafficLiveTickDto,
    val nextState: SubscriptionTrafficLiveTickState
)

object SubscriptionTrafficLiveTickBuilder {

    fun buildFromQueueRow(
        subscriptionId: Int,
        queueRow: Map<String, String>?,
        previous: SubscriptionTrafficLiveTickState,
        intervalSeconds: Double = 1.0,
        timestamp: LocalDateTime = LocalDateTime.now()
    ): SubscriptionTrafficLiveTickBuildResult {
        val tickTimestamp = timestamp.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        if (queueRow == null) {
            return SubscriptionTrafficLiveTickBuildResult(
                tick = SubscriptionTrafficLiveTickDto(
                    subscriptionId = subscriptionId,
                    timestamp = tickTimestamp,
                    rxMbps = null,
                    txMbps = null,
                    rxBytesDelta = 0L,
                    txBytesDelta = 0L,
                    sessionRxBytes = previous.sessionRxBytes,
                    sessionTxBytes = previous.sessionTxBytes,
                    queueFound = false
                ),
                nextState = previous
            )
        }

        val bytes = RouterOsTrafficCounterParser.parseUpDown(queueRow["bytes"])
        val rate = RouterOsTrafficCounterParser.parseUpDown(queueRow["rate"])
        if (bytes == null) {
            return SubscriptionTrafficLiveTickBuildResult(
                tick = SubscriptionTrafficLiveTickDto(
                    subscriptionId = subscriptionId,
                    timestamp = tickTimestamp,
                    rxMbps = null,
                    txMbps = null,
                    rxBytesDelta = 0L,
                    txBytesDelta = 0L,
                    sessionRxBytes = previous.sessionRxBytes,
                    sessionTxBytes = previous.sessionTxBytes,
                    queueFound = false
                ),
                nextState = previous
            )
        }

        val txBytes = bytes.first
        val rxBytes = bytes.second
        val counterResetRx = previous.lastRxBytes != null && rxBytes < previous.lastRxBytes!!
        val counterResetTx = previous.lastTxBytes != null && txBytes < previous.lastTxBytes!!
        val rxDelta = RouterOsTrafficCounterParser.computeDelta(previous.lastRxBytes, rxBytes, counterResetRx)
        val txDelta = RouterOsTrafficCounterParser.computeDelta(previous.lastTxBytes, txBytes, counterResetTx)

        val rxMbps = rate?.second?.let { RouterOsTrafficCounterParser.bytesToMbps(it, 1.0) }
            ?: RouterOsTrafficCounterParser.bytesToMbps(rxDelta, intervalSeconds)
        val txMbps = rate?.first?.let { RouterOsTrafficCounterParser.bytesToMbps(it, 1.0) }
            ?: RouterOsTrafficCounterParser.bytesToMbps(txDelta, intervalSeconds)

        val nextState = SubscriptionTrafficLiveTickState(
            lastRxBytes = rxBytes,
            lastTxBytes = txBytes,
            sessionRxBytes = previous.sessionRxBytes + rxDelta,
            sessionTxBytes = previous.sessionTxBytes + txDelta
        )

        return SubscriptionTrafficLiveTickBuildResult(
            tick = SubscriptionTrafficLiveTickDto(
                subscriptionId = subscriptionId,
                timestamp = tickTimestamp,
                rxMbps = rxMbps,
                txMbps = txMbps,
                rxBytesDelta = rxDelta,
                txBytesDelta = txDelta,
                sessionRxBytes = nextState.sessionRxBytes,
                sessionTxBytes = nextState.sessionTxBytes,
                queueFound = true
            ),
            nextState = nextState
        )
    }

    fun findQueueRowForIp(queues: List<Map<String, String>>, ip: String): Map<String, String>? {
        val normalizedIp = ip.trim()
        if (normalizedIp.isEmpty()) return null
        return queues.firstOrNull { row ->
            RouterOsTrafficCounterParser.normalizeTarget(row["target"]) == normalizedIp
        }
    }
}
