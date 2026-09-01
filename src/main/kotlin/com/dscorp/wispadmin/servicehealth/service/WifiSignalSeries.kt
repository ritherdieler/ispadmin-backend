package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.domain.UtcInstantText
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.WifiStationHourly
import com.dscorp.wispadmin.servicehealth.domain.WifiStationSample
import java.time.Instant

object WifiSignalSeries {
    fun useHourly(from: Instant, to: Instant, rawMaxDays: Long): Boolean {
        val seconds = to.epochSecond - from.epochSecond
        return seconds > rawMaxDays.coerceAtLeast(1) * 86400
    }

    fun mapRaw(sample: WifiStationSample): Map<String, Any?> = mapOf(
        "reading_id" to sample.countSampleId,
        "station_key" to sample.stationKey,
        "display_name" to sample.displayName,
        "observed_at" to UtcInstantText.formatApi(sample.observedAt),
        "band" to sample.band,
        "rssi" to sample.rssi,
        "snr" to sample.snr,
        "rx_rate" to sample.rxRate,
        "tx_rate" to sample.txRate,
        "quality_status" to sample.qualityStatus,
    )

    fun mapHourly(bucket: WifiStationHourly): Map<String, Any?> = mapOf(
        "reading_id" to bucket.id,
        "station_key" to bucket.stationKey,
        "display_name" to bucket.displayName,
        "observed_at" to UtcInstantText.formatApi(bucket.bucketStart),
        "band" to bucket.band,
        "rssi" to bucket.rssiMin,
        "snr" to bucket.snrMin,
        "rx_rate" to null,
        "tx_rate" to null,
        "quality_status" to Quality.FRESH,
    )
}
