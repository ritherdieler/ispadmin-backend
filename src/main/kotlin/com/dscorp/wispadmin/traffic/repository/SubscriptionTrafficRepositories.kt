package com.dscorp.wispadmin.traffic.repository

import com.dscorp.wispadmin.traffic.entity.NetworkTrafficHourOfDay
import com.dscorp.wispadmin.traffic.entity.NetworkTrafficHourOfDayId
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficCounterState
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficDaily
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficHourly
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficMonthly
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficFiveMinute
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationRun
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationLayer
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationWatermark
import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyEvent
import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyStatus
import com.dscorp.wispadmin.traffic.entity.TrafficAnomalyType
import com.dscorp.wispadmin.traffic.entity.TrafficSourceRun
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

interface SubscriptionTrafficSampleRepository : JpaRepository<SubscriptionTrafficSample, Long> {
    fun findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(
        subscriptionId: Int,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<SubscriptionTrafficSample>

    fun findTopBySubscriptionIdOrderByBucketStartDesc(subscriptionId: Int): SubscriptionTrafficSample?

    fun findBySubscriptionIdAndBucketStart(subscriptionId: Int, bucketStart: LocalDateTime): SubscriptionTrafficSample?

    fun deleteBySubscriptionId(subscriptionId: Int): Long

    @Query(
        """
        SELECT s FROM SubscriptionTrafficSample s
        WHERE s.bucketStart >= :from AND s.bucketStart < :to
        ORDER BY s.subscriptionId, s.bucketStart
        """
    )
    fun findAllInBucketRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<SubscriptionTrafficSample>

    @Query(
        value = """
        WITH ranked AS (
            SELECT subscription_id,
                   rx_bytes_delta,
                   tx_bytes_delta,
                   avg_mbps_down,
                   avg_mbps_up,
                   ROW_NUMBER() OVER (PARTITION BY subscription_id ORDER BY avg_mbps_down) AS rn_down,
                   ROW_NUMBER() OVER (PARTITION BY subscription_id ORDER BY avg_mbps_up) AS rn_up,
                   COUNT(*) OVER (PARTITION BY subscription_id) AS sample_count
            FROM subscription_traffic_sample
            WHERE subscription_id IN (:subscriptionIds)
              AND bucket_start >= :from
              AND bucket_start < :to
              AND sample_status = 'OK'
        )
        SELECT subscription_id AS subscriptionId,
               CAST(SUM(rx_bytes_delta) AS SIGNED) AS rxBytes,
               CAST(SUM(tx_bytes_delta) AS SIGNED) AS txBytes,
               MAX(CASE WHEN rn_down = CEIL(0.95 * sample_count) THEN avg_mbps_down END) AS p95MbpsDown,
               MAX(CASE WHEN rn_up = CEIL(0.95 * sample_count) THEN avg_mbps_up END) AS p95MbpsUp,
               MAX(sample_count) AS sampleCount
        FROM ranked
        GROUP BY subscription_id
        """,
        nativeQuery = true
    )
    fun summarizeInBucketRange(
        @Param("subscriptionIds") subscriptionIds: Collection<Int>,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<SubscriptionTrafficRawSummaryProjection>

    @Modifying
    @Query("DELETE FROM SubscriptionTrafficSample s WHERE s.bucketStart < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDateTime): Int

    @Query("SELECT MIN(s.bucketStart) FROM SubscriptionTrafficSample s")
    fun findMinBucketStart(): LocalDateTime?
}

interface SubscriptionTrafficRawSummaryProjection {
    fun getSubscriptionId(): Int
    fun getRxBytes(): Long
    fun getTxBytes(): Long
    fun getP95MbpsDown(): Double
    fun getP95MbpsUp(): Double
    fun getSampleCount(): Int
}

interface SubscriptionTrafficFiveMinuteRepository : JpaRepository<SubscriptionTrafficFiveMinute, Long> {
    fun findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(subscriptionId: Int, from: LocalDateTime, to: LocalDateTime): List<SubscriptionTrafficFiveMinute>
    fun findBySubscriptionIdAndBucketStart(subscriptionId: Int, bucketStart: LocalDateTime): SubscriptionTrafficFiveMinute?
    @Query("SELECT f FROM SubscriptionTrafficFiveMinute f WHERE f.bucketStart >= :from AND f.bucketStart < :to")
    fun findInBucketRange(@Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): List<SubscriptionTrafficFiveMinute>
    @Modifying @Query("DELETE FROM SubscriptionTrafficFiveMinute f WHERE f.bucketStart < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDateTime): Int

    @Query("SELECT MIN(f.bucketStart) FROM SubscriptionTrafficFiveMinute f")
    fun findMinBucketStart(): LocalDateTime?
}

interface TrafficSourceRunRepository : JpaRepository<TrafficSourceRun, Long> {
    fun findTopByHostDeviceIdOrderByStartedAtDesc(hostDeviceId: Int): TrafficSourceRun?
    fun findTop100ByOrderByStartedAtDesc(): List<TrafficSourceRun>
}

interface TrafficAnomalyEventRepository : JpaRepository<TrafficAnomalyEvent, Long> {
    @Query("select e from TrafficAnomalyEvent e where e.lastEvaluatedAt > :at or (e.lastEvaluatedAt = :at and e.id > :id) order by e.lastEvaluatedAt, e.id")
    fun findChanges(@Param("at") at: LocalDateTime, @Param("id") id: Long, page: org.springframework.data.domain.Pageable): List<TrafficAnomalyEvent>

    fun findByDedupeKey(dedupeKey: String): TrafficAnomalyEvent?
    fun findByEventStatusOrderByStartedAtDesc(status: TrafficAnomalyStatus): List<TrafficAnomalyEvent>
    fun findBySubscriptionIdAndStartedAtBetweenOrderByStartedAtDesc(subscriptionId: Int, from: LocalDateTime, to: LocalDateTime): List<TrafficAnomalyEvent>
    fun findByAnomalyTypeAndEventStatus(type: TrafficAnomalyType, status: TrafficAnomalyStatus): List<TrafficAnomalyEvent>
    fun findTopByAnomalyTypeAndSubscriptionIdAndEventStatusOrderByStartedAtDesc(type: TrafficAnomalyType, subscriptionId: Int, status: TrafficAnomalyStatus): TrafficAnomalyEvent?
    fun findTopByAnomalyTypeAndHostDeviceIdAndEventStatusOrderByStartedAtDesc(type: TrafficAnomalyType, hostDeviceId: Int, status: TrafficAnomalyStatus): TrafficAnomalyEvent?
}

interface SubscriptionTrafficHourlyRepository : JpaRepository<SubscriptionTrafficHourly, Long> {
    fun findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(
        subscriptionId: Int,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<SubscriptionTrafficHourly>

    fun findBySubscriptionIdAndBucketStart(subscriptionId: Int, bucketStart: LocalDateTime): SubscriptionTrafficHourly?

    @Modifying
    @Query("DELETE FROM SubscriptionTrafficHourly h WHERE h.bucketStart < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDateTime): Int

    @Query(
        """
        SELECT h FROM SubscriptionTrafficHourly h
        WHERE h.bucketStart >= :from AND h.bucketStart < :to
        """
    )
    fun findInBucketRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<SubscriptionTrafficHourly>

    @Query(
        """
        SELECT HOUR(h.bucketStart) AS hourOfDay,
               SUM(h.rxBytesTotal) AS rxBytes,
               SUM(h.txBytesTotal) AS txBytes
        FROM SubscriptionTrafficHourly h
        WHERE h.bucketStart >= :from
        GROUP BY HOUR(h.bucketStart)
        ORDER BY HOUR(h.bucketStart)
        """
    )
    fun aggregateNetworkHourlyProfile(@Param("from") from: LocalDateTime): List<NetworkHourAggregateProjection>

    @Query("SELECT MIN(h.bucketStart) FROM SubscriptionTrafficHourly h")
    fun findMinBucketStart(): LocalDateTime?
}

interface SubscriptionTrafficDailyRepository : JpaRepository<SubscriptionTrafficDaily, Long> {
    fun findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(
        subscriptionId: Int,
        from: LocalDate,
        to: LocalDate
    ): List<SubscriptionTrafficDaily>

    fun findBySubscriptionIdAndBucketStart(subscriptionId: Int, bucketStart: LocalDate): SubscriptionTrafficDaily?

    @Modifying
    @Query("DELETE FROM SubscriptionTrafficDaily d WHERE d.bucketStart < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDate): Int

    @Query(
        """
        SELECT d FROM SubscriptionTrafficDaily d
        WHERE d.bucketStart >= :from AND d.bucketStart < :to
        """
    )
    fun findInBucketRange(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): List<SubscriptionTrafficDaily>

    @Query(
        """
        SELECT d.bucketStart AS bucketDate,
               SUM(d.rxBytesTotal) AS rxBytes,
               SUM(d.txBytesTotal) AS txBytes
        FROM SubscriptionTrafficDaily d
        WHERE d.bucketStart >= :fromDate
        GROUP BY d.bucketStart
        ORDER BY d.bucketStart
        """
    )
    fun aggregateNetworkDailyTrend(@Param("fromDate") fromDate: LocalDate): List<NetworkDayAggregateProjection>
}

interface SubscriptionTrafficMonthlyRepository : JpaRepository<SubscriptionTrafficMonthly, Long> {
    fun findBySubscriptionIdAndYearMonth(subscriptionId: Int, yearMonth: String): SubscriptionTrafficMonthly?

    fun findBySubscriptionIdOrderByYearMonthDesc(subscriptionId: Int): List<SubscriptionTrafficMonthly>
}

interface SubscriptionTrafficCounterStateRepository : JpaRepository<SubscriptionTrafficCounterState, Int> {
    fun findByHostDeviceId(hostDeviceId: Int): List<SubscriptionTrafficCounterState>
}

interface NetworkTrafficHourOfDayRepository : JpaRepository<NetworkTrafficHourOfDay, NetworkTrafficHourOfDayId> {
    @Query(
        """
        SELECT n.hourOfDay AS hourOfDay,
               SUM(n.rxBytesTotal) AS rxBytes,
               SUM(n.txBytesTotal) AS txBytes
        FROM NetworkTrafficHourOfDay n
        WHERE n.bucketDate >= :fromDate
        GROUP BY n.hourOfDay
        ORDER BY n.hourOfDay
        """
    )
    fun aggregateHourlyProfile(@Param("fromDate") fromDate: LocalDate): List<NetworkHourAggregateProjection>

    @Query(
        """
        SELECT n.bucketDate AS bucketDate,
               SUM(n.rxBytesTotal) AS rxBytes,
               SUM(n.txBytesTotal) AS txBytes
        FROM NetworkTrafficHourOfDay n
        WHERE n.bucketDate >= :fromDate
        GROUP BY n.bucketDate
        ORDER BY n.bucketDate
        """
    )
    fun aggregateDailyTrend(@Param("fromDate") fromDate: LocalDate): List<NetworkDayAggregateProjection>

    @Modifying
    @Query("DELETE FROM NetworkTrafficHourOfDay n WHERE n.bucketDate < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDate): Int
}

interface NetworkHourAggregateProjection {
    fun getHourOfDay(): Int
    fun getRxBytes(): Long
    fun getTxBytes(): Long
}

interface NetworkDayAggregateProjection {
    fun getBucketDate(): LocalDate
    fun getRxBytes(): Long
    fun getTxBytes(): Long
}

interface TrafficAggregationWatermarkRepository : JpaRepository<TrafficAggregationWatermark, TrafficAggregationLayer>

interface TrafficAggregationRunRepository : JpaRepository<TrafficAggregationRun, Long> {
    fun findTopByLayerOrderByStartedAtDesc(layer: TrafficAggregationLayer): TrafficAggregationRun?
}
