package com.dscorp.wispadmin.servicehealth.service

import java.time.Instant
import java.time.temporal.ChronoUnit

object WifiStationHourlyAggregator {
    data class Sample(
        val subscriptionId: Int,
        val stationKey: String,
        val band: String,
        val observedAt: Instant,
        val rssi: Double?,
        val snr: Double?,
        val displayName: String?,
    )

    data class Bucket(
        val subscriptionId: Int,
        val stationKey: String,
        val band: String,
        val bucketStart: Instant,
        val rssiMin: Double?,
        val rssiAvg: Double?,
        val rssiMax: Double?,
        val snrMin: Double?,
        val snrAvg: Double?,
        val sampleCount: Int,
        val displayName: String?,
    )

    fun bucketStart(at: Instant): Instant = at.truncatedTo(ChronoUnit.HOURS)

    fun aggregate(samples: List<Sample>, from: Instant, to: Instant): List<Bucket> {
        return samples
            .filter { !it.observedAt.isBefore(from) && it.observedAt.isBefore(to) }
            .groupBy { Triple(it.subscriptionId, it.stationKey, it.band) to bucketStart(it.observedAt) }
            .map { (key, group) ->
                val rssi = group.mapNotNull { it.rssi }
                val snr = group.mapNotNull { it.snr }
                val named = group.filter { !it.displayName.isNullOrBlank() }.maxByOrNull { it.observedAt }
                Bucket(
                    subscriptionId = key.first.first,
                    stationKey = key.first.second,
                    band = key.first.third,
                    bucketStart = key.second,
                    rssiMin = rssi.minOrNull(),
                    rssiAvg = rssi.takeIf { it.isNotEmpty() }?.average(),
                    rssiMax = rssi.maxOrNull(),
                    snrMin = snr.minOrNull(),
                    snrAvg = snr.takeIf { it.isNotEmpty() }?.average(),
                    sampleCount = group.size,
                    displayName = named?.displayName,
                )
            }
            .sortedWith(compareBy({ it.bucketStart }, { it.stationKey }, { it.band }))
    }
}
