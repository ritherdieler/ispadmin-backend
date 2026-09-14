package com.dscorp.wispadmin.servicehealth.service

import java.time.Instant
import java.time.temporal.ChronoUnit

object OpticalDailyAggregator {
    data class Sample(
        val subscriptionId: Int?,
        val onuId: Long,
        val onuSn: String,
        val oltId: Long,
        val board: Int,
        val port: Int,
        val onuIndex: Int,
        val observedAt: Instant,
        val onuRxDbm: Double?,
        val onuTxDbm: Double?,
        val oltRxDbm: Double?,
    )

    data class Bucket(
        val subscriptionId: Int?,
        val onuId: Long,
        val onuSn: String,
        val oltId: Long,
        val board: Int,
        val port: Int,
        val onuIndex: Int,
        val bucketStart: Instant,
        val onuRxMin: Double?,
        val onuRxAvg: Double?,
        val onuRxMax: Double?,
        val onuTxMin: Double?,
        val onuTxAvg: Double?,
        val onuTxMax: Double?,
        val oltRxMin: Double?,
        val oltRxAvg: Double?,
        val oltRxMax: Double?,
        val sampleCount: Int,
    )

    fun bucketStart(at: Instant): Instant = at.truncatedTo(ChronoUnit.DAYS)

    fun aggregate(samples: List<Sample>, from: Instant, to: Instant): List<Bucket> {
        return samples
            .filter { !it.observedAt.isBefore(from) && it.observedAt.isBefore(to) }
            .groupBy { it.onuId to bucketStart(it.observedAt) }
            .map { (_, group) ->
                val latest = group.maxBy { it.observedAt }
                val rx = group.mapNotNull { it.onuRxDbm }
                val tx = group.mapNotNull { it.onuTxDbm }
                val olt = group.mapNotNull { it.oltRxDbm }
                Bucket(
                    subscriptionId = latest.subscriptionId,
                    onuId = latest.onuId,
                    onuSn = latest.onuSn,
                    oltId = latest.oltId,
                    board = latest.board,
                    port = latest.port,
                    onuIndex = latest.onuIndex,
                    bucketStart = bucketStart(latest.observedAt),
                    onuRxMin = rx.minOrNull(),
                    onuRxAvg = rx.takeIf { it.isNotEmpty() }?.average(),
                    onuRxMax = rx.maxOrNull(),
                    onuTxMin = tx.minOrNull(),
                    onuTxAvg = tx.takeIf { it.isNotEmpty() }?.average(),
                    onuTxMax = tx.maxOrNull(),
                    oltRxMin = olt.minOrNull(),
                    oltRxAvg = olt.takeIf { it.isNotEmpty() }?.average(),
                    oltRxMax = olt.maxOrNull(),
                    sampleCount = group.size,
                )
            }
            .sortedWith(compareBy({ it.bucketStart }, { it.onuId }))
    }
}
