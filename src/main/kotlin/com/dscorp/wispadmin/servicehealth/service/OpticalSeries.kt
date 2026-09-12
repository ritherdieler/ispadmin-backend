package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.domain.OpticalDailySample
import com.dscorp.wispadmin.servicehealth.domain.OpticalSample
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.UtcInstantText
import java.time.Instant

object OpticalSeries {
    fun useDaily(from: Instant, to: Instant, rawMaxDays: Long): Boolean {
        val seconds = to.epochSecond - from.epochSecond
        return seconds > rawMaxDays.coerceAtLeast(1) * 86400
    }

    fun mapRaw(sample: OpticalSample): Map<String, Any?> = mapOf(
        "id" to sample.id,
        "observed_at" to UtcInstantText.formatApi(sample.observedAt),
        "collected_at" to UtcInstantText.formatApi(sample.collectedAt),
        "onu_id" to sample.onuId,
        "onu_sn" to sample.onuSn,
        "olt_id" to sample.oltId,
        "board" to sample.board,
        "port" to sample.port,
        "onu_rx_dbm" to sample.onuRxDbm,
        "onu_tx_dbm" to sample.onuTxDbm,
        "olt_rx_dbm" to sample.oltRxDbm,
        "temperature_c" to sample.temperatureC,
        "distance_m" to sample.distanceM,
        "bias_ma" to sample.biasMa,
        "voltage_v" to sample.voltageV,
        "quality_status" to sample.qualityStatus,
    )

    fun mapDaily(bucket: OpticalDailySample): Map<String, Any?> = mapOf(
        "id" to bucket.id,
        "observed_at" to UtcInstantText.formatApi(bucket.bucketStart),
        "collected_at" to UtcInstantText.formatApi(bucket.bucketStart),
        "onu_id" to bucket.onuId,
        "onu_sn" to bucket.onuSn,
        "olt_id" to bucket.oltId,
        "board" to bucket.board,
        "port" to bucket.port,
        "onu_rx_dbm" to bucket.onuRxAvg,
        "onu_rx_dbm_min" to bucket.onuRxMin,
        "onu_rx_dbm_max" to bucket.onuRxMax,
        "onu_tx_dbm" to bucket.onuTxAvg,
        "onu_tx_dbm_min" to bucket.onuTxMin,
        "onu_tx_dbm_max" to bucket.onuTxMax,
        "olt_rx_dbm" to bucket.oltRxAvg,
        "olt_rx_dbm_min" to bucket.oltRxMin,
        "olt_rx_dbm_max" to bucket.oltRxMax,
        "sample_count" to bucket.sampleCount,
        "temperature_c" to null,
        "distance_m" to null,
        "bias_ma" to null,
        "voltage_v" to null,
        "quality_status" to Quality.FRESH,
    )
}
