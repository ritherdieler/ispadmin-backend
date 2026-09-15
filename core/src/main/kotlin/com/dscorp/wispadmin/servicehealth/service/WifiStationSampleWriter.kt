package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.domain.WifiStationSample
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

/**
 * One round trip per Inform instead of one per station.
 *
 * The entity uses `GenerationType.IDENTITY`, which makes Hibernate disable JDBC
 * insert batching altogether, so `saveAll` would still issue N statements. At
 * 3000 ONUs this is ~576k rows a day and the round trips dominate.
 */
@Component
class WifiStationSampleWriter(private val jdbc: JdbcTemplate) {
    private fun utc() = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    private fun PreparedStatement.setInstant(index: Int, value: Instant) =
        setTimestamp(index, Timestamp.from(value), utc())

    private fun PreparedStatement.setDouble(index: Int, value: Double?) =
        if (value == null) setNull(index, Types.DOUBLE) else setDouble(index, value)

    private fun PreparedStatement.setLong(index: Int, value: Long?) =
        if (value == null) setNull(index, Types.BIGINT) else setLong(index, value)

    fun insertAll(samples: List<WifiStationSample>) {
        if (samples.isEmpty()) return
        jdbc.batchUpdate(
            """
            insert into acs_wifi_station_sample
                (count_sample_id, subscription_id, station_key, band, display_name,
                 observed_at, collected_at, rssi, snr, noise, rx_rate, tx_rate,
                 packets_tx, packets_rx, quality_status)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            samples,
            samples.size,
        ) { ps, sample ->
            ps.setLong(1, sample.countSampleId)
            ps.setInt(2, sample.subscriptionId)
            ps.setString(3, sample.stationKey)
            ps.setString(4, sample.band)
            ps.setString(5, sample.displayName)
            ps.setInstant(6, sample.observedAt)
            ps.setInstant(7, sample.collectedAt)
            ps.setDouble(8, sample.rssi)
            ps.setDouble(9, sample.snr)
            ps.setDouble(10, sample.noise)
            ps.setDouble(11, sample.rxRate)
            ps.setDouble(12, sample.txRate)
            ps.setLong(13, sample.packetsTx)
            ps.setLong(14, sample.packetsRx)
            ps.setString(15, sample.qualityStatus.name)
        }
    }
}
