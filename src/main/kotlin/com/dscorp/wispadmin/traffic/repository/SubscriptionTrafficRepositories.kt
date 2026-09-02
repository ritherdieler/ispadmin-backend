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

    @Modifying
    @Query("DELETE FROM SubscriptionTrafficSample s WHERE s.bucketStart < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDateTime): Int

    @Query("SELECT MIN(s.bucketStart) FROM SubscriptionTrafficSample s")
    fun findMinBucketStart(): LocalDateTime?
}

interface BandwidthNetworkBucketProjection {
    fun getBucketStart(): LocalDateTime
    fun getRxBytes(): Long
    fun getTxBytes(): Long
    fun getAvgMbpsDown(): Double
    fun getAvgMbpsUp(): Double
    fun getP95MbpsDown(): Double
    fun getP95MbpsUp(): Double
    fun getCoveragePct(): Double
}

interface BandwidthNetworkDayBucketProjection {
    fun getBucketStart(): LocalDate
    fun getRxBytes(): Long
    fun getTxBytes(): Long
    fun getAvgMbpsDown(): Double
    fun getAvgMbpsUp(): Double
    fun getP95MbpsDown(): Double
    fun getP95MbpsUp(): Double
    fun getCoveragePct(): Double
}

interface SubscriptionTrafficFiveMinuteRepository : JpaRepository<SubscriptionTrafficFiveMinute, Long> {
    fun findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(subscriptionId: Int, from: LocalDateTime, to: LocalDateTime): List<SubscriptionTrafficFiveMinute>
    fun findBySubscriptionIdAndBucketStart(subscriptionId: Int, bucketStart: LocalDateTime): SubscriptionTrafficFiveMinute?
    @Query("SELECT f FROM SubscriptionTrafficFiveMinute f WHERE f.bucketStart >= :from AND f.bucketStart < :to")
    fun findInBucketRange(@Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): List<SubscriptionTrafficFiveMinute>

    @Query(
        """
        SELECT f FROM SubscriptionTrafficFiveMinute f
        WHERE f.bucketStart >= :from AND f.bucketStart < :to
          AND f.subscriptionId IN :subscriptionIds
        """
    )
    fun findInBucketRangeForSubscriptions(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("subscriptionIds") subscriptionIds: Collection<Int>
    ): List<SubscriptionTrafficFiveMinute>

    @Query(
        """
        SELECT f.bucketStart AS bucketStart,
               SUM(f.rxBytesTotal) AS rxBytes,
               SUM(f.txBytesTotal) AS txBytes,
               SUM(f.avgMbpsDown) AS avgMbpsDown,
               SUM(f.avgMbpsUp) AS avgMbpsUp,
               SUM(f.p95MbpsDown) AS p95MbpsDown,
               SUM(f.p95MbpsUp) AS p95MbpsUp,
               AVG(f.coveragePct) AS coveragePct
        FROM SubscriptionTrafficFiveMinute f
        WHERE f.bucketStart >= :from AND f.bucketStart < :to
        GROUP BY f.bucketStart
        ORDER BY f.bucketStart
        """
    )
    fun aggregateNetworkBuckets(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<BandwidthNetworkBucketProjection>

    @Query(
        """
        SELECT f.bucketStart AS bucketStart,
               SUM(f.rxBytesTotal) AS rxBytes,
               SUM(f.txBytesTotal) AS txBytes,
               SUM(f.avgMbpsDown) AS avgMbpsDown,
               SUM(f.avgMbpsUp) AS avgMbpsUp,
               SUM(f.p95MbpsDown) AS p95MbpsDown,
               SUM(f.p95MbpsUp) AS p95MbpsUp,
               AVG(f.coveragePct) AS coveragePct
        FROM SubscriptionTrafficFiveMinute f
        WHERE f.bucketStart >= :from AND f.bucketStart < :to
          AND f.hostDeviceId = :hostDeviceId
        GROUP BY f.bucketStart
        ORDER BY f.bucketStart
        """
    )
    fun aggregateNetworkBucketsByHost(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("hostDeviceId") hostDeviceId: Int
    ): List<BandwidthNetworkBucketProjection>

    @Query(
        """
        SELECT f.bucketStart AS bucketStart,
               SUM(f.rxBytesTotal) AS rxBytes,
               SUM(f.txBytesTotal) AS txBytes,
               SUM(f.avgMbpsDown) AS avgMbpsDown,
               SUM(f.avgMbpsUp) AS avgMbpsUp,
               SUM(f.p95MbpsDown) AS p95MbpsDown,
               SUM(f.p95MbpsUp) AS p95MbpsUp,
               AVG(f.coveragePct) AS coveragePct
        FROM SubscriptionTrafficFiveMinute f
        WHERE f.bucketStart >= :from AND f.bucketStart < :to
          AND f.subscriptionId IN :subscriptionIds
        GROUP BY f.bucketStart
        ORDER BY f.bucketStart
        """
    )
    fun aggregateNetworkBucketsBySubscriptions(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("subscriptionIds") subscriptionIds: Collection<Int>
    ): List<BandwidthNetworkBucketProjection>

    @Query(
        """
        SELECT COUNT(DISTINCT f.subscriptionId)
        FROM SubscriptionTrafficFiveMinute f
        WHERE f.bucketStart >= :from AND f.bucketStart < :to
        """
    )
    fun countDistinctSubscriptions(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): Long

    @Query(
        """
        SELECT COUNT(DISTINCT f.subscriptionId)
        FROM SubscriptionTrafficFiveMinute f
        WHERE f.bucketStart >= :from AND f.bucketStart < :to
          AND f.hostDeviceId = :hostDeviceId
        """
    )
    fun countDistinctSubscriptionsByHost(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("hostDeviceId") hostDeviceId: Int
    ): Long

    @Query(
        """
        SELECT COUNT(DISTINCT f.subscriptionId)
        FROM SubscriptionTrafficFiveMinute f
        WHERE f.bucketStart >= :from AND f.bucketStart < :to
          AND f.subscriptionId IN :subscriptionIds
        """
    )
    fun countDistinctSubscriptionsByIds(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("subscriptionIds") subscriptionIds: Collection<Int>
    ): Long

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
    fun countByEventStatus(status: TrafficAnomalyStatus): Long
    fun findBySubscriptionIdAndStartedAtBetweenOrderByStartedAtDesc(subscriptionId: Int, from: LocalDateTime, to: LocalDateTime): List<TrafficAnomalyEvent>
    fun findByAnomalyTypeAndEventStatus(type: TrafficAnomalyType, status: TrafficAnomalyStatus): List<TrafficAnomalyEvent>
    fun findTopByAnomalyTypeAndSubscriptionIdAndEventStatusOrderByStartedAtDesc(type: TrafficAnomalyType, subscriptionId: Int, status: TrafficAnomalyStatus): TrafficAnomalyEvent?
    fun findTopByAnomalyTypeAndHostDeviceIdAndEventStatusOrderByStartedAtDesc(type: TrafficAnomalyType, hostDeviceId: Int, status: TrafficAnomalyStatus): TrafficAnomalyEvent?

    @Query(
        """
        SELECT COUNT(e) FROM TrafficAnomalyEvent e
        WHERE e.eventStatus = com.dscorp.wispadmin.traffic.entity.TrafficAnomalyStatus.OPEN
          AND (e.subscriptionId IS NULL OR e.subscriptionId IN :subscriptionIds)
        """
    )
    fun countOpenForSubscriptions(@Param("subscriptionIds") subscriptionIds: Collection<Int>): Long

    @Query(
        """
        SELECT e FROM TrafficAnomalyEvent e
        WHERE (:type IS NULL OR e.anomalyType = :type)
          AND (:status IS NULL OR e.eventStatus = :status)
          AND (:subscriptionId IS NULL OR e.subscriptionId = :subscriptionId)
          AND (:routerId IS NULL OR e.hostDeviceId = :routerId)
        ORDER BY e.startedAt DESC
        """
    )
    fun findFiltered(
        @Param("type") type: TrafficAnomalyType?,
        @Param("status") status: TrafficAnomalyStatus?,
        @Param("subscriptionId") subscriptionId: Int?,
        @Param("routerId") routerId: Int?
    ): List<TrafficAnomalyEvent>
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
        SELECT DISTINCT h.subscriptionId
        FROM SubscriptionTrafficHourly h
        WHERE h.bucketStart >= :from AND h.bucketStart < :to
        ORDER BY h.subscriptionId
        """
    )
    fun findDistinctSubscriptionIdsInBucketRange(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<Int>

    @Query(
        """
        SELECT h FROM SubscriptionTrafficHourly h
        WHERE h.bucketStart >= :from AND h.bucketStart < :to
          AND h.subscriptionId IN :subscriptionIds
        """
    )
    fun findInBucketRangeForSubscriptions(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("subscriptionIds") subscriptionIds: Collection<Int>
    ): List<SubscriptionTrafficHourly>

    @Query(
        """
        SELECT h.bucketStart AS bucketStart,
               SUM(h.rxBytesTotal) AS rxBytes,
               SUM(h.txBytesTotal) AS txBytes,
               SUM(h.avgMbpsDown) AS avgMbpsDown,
               SUM(h.avgMbpsUp) AS avgMbpsUp,
               SUM(h.p95MbpsDown) AS p95MbpsDown,
               SUM(h.p95MbpsUp) AS p95MbpsUp,
               AVG(h.coveragePct) AS coveragePct
        FROM SubscriptionTrafficHourly h
        WHERE h.bucketStart >= :from AND h.bucketStart < :to
        GROUP BY h.bucketStart
        ORDER BY h.bucketStart
        """
    )
    fun aggregateNetworkBuckets(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<BandwidthNetworkBucketProjection>

    @Query(
        """
        SELECT h.bucketStart AS bucketStart,
               SUM(h.rxBytesTotal) AS rxBytes,
               SUM(h.txBytesTotal) AS txBytes,
               SUM(h.avgMbpsDown) AS avgMbpsDown,
               SUM(h.avgMbpsUp) AS avgMbpsUp,
               SUM(h.p95MbpsDown) AS p95MbpsDown,
               SUM(h.p95MbpsUp) AS p95MbpsUp,
               AVG(h.coveragePct) AS coveragePct
        FROM SubscriptionTrafficHourly h
        WHERE h.bucketStart >= :from AND h.bucketStart < :to
          AND h.subscriptionId IN :subscriptionIds
        GROUP BY h.bucketStart
        ORDER BY h.bucketStart
        """
    )
    fun aggregateNetworkBucketsBySubscriptions(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("subscriptionIds") subscriptionIds: Collection<Int>
    ): List<BandwidthNetworkBucketProjection>

    @Query(
        """
        SELECT COUNT(DISTINCT h.subscriptionId)
        FROM SubscriptionTrafficHourly h
        WHERE h.bucketStart >= :from AND h.bucketStart < :to
        """
    )
    fun countDistinctSubscriptions(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): Long

    @Query(
        """
        SELECT COUNT(DISTINCT h.subscriptionId)
        FROM SubscriptionTrafficHourly h
        WHERE h.bucketStart >= :from AND h.bucketStart < :to
          AND h.subscriptionId IN :subscriptionIds
        """
    )
    fun countDistinctSubscriptionsByIds(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("subscriptionIds") subscriptionIds: Collection<Int>
    ): Long

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
        SELECT DISTINCT d.subscriptionId
        FROM SubscriptionTrafficDaily d
        WHERE d.bucketStart >= :from AND d.bucketStart < :to
        ORDER BY d.subscriptionId
        """
    )
    fun findDistinctSubscriptionIdsInBucketRange(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): List<Int>

    @Query(
        """
        SELECT d FROM SubscriptionTrafficDaily d
        WHERE d.bucketStart >= :from AND d.bucketStart < :to
          AND d.subscriptionId IN :subscriptionIds
        """
    )
    fun findInBucketRangeForSubscriptions(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("subscriptionIds") subscriptionIds: Collection<Int>
    ): List<SubscriptionTrafficDaily>

    @Query(
        """
        SELECT d.bucketStart AS bucketStart,
               SUM(d.rxBytesTotal) AS rxBytes,
               SUM(d.txBytesTotal) AS txBytes,
               SUM(d.avgMbpsDown) AS avgMbpsDown,
               SUM(d.avgMbpsUp) AS avgMbpsUp,
               SUM(d.p95MbpsDown) AS p95MbpsDown,
               SUM(d.p95MbpsUp) AS p95MbpsUp,
               AVG(d.coveragePct) AS coveragePct
        FROM SubscriptionTrafficDaily d
        WHERE d.bucketStart >= :from AND d.bucketStart < :to
        GROUP BY d.bucketStart
        ORDER BY d.bucketStart
        """
    )
    fun aggregateNetworkBuckets(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): List<BandwidthNetworkDayBucketProjection>

    @Query(
        """
        SELECT d.bucketStart AS bucketStart,
               SUM(d.rxBytesTotal) AS rxBytes,
               SUM(d.txBytesTotal) AS txBytes,
               SUM(d.avgMbpsDown) AS avgMbpsDown,
               SUM(d.avgMbpsUp) AS avgMbpsUp,
               SUM(d.p95MbpsDown) AS p95MbpsDown,
               SUM(d.p95MbpsUp) AS p95MbpsUp,
               AVG(d.coveragePct) AS coveragePct
        FROM SubscriptionTrafficDaily d
        WHERE d.bucketStart >= :from AND d.bucketStart < :to
          AND d.subscriptionId IN :subscriptionIds
        GROUP BY d.bucketStart
        ORDER BY d.bucketStart
        """
    )
    fun aggregateNetworkBucketsBySubscriptions(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("subscriptionIds") subscriptionIds: Collection<Int>
    ): List<BandwidthNetworkDayBucketProjection>

    @Query(
        """
        SELECT COUNT(DISTINCT d.subscriptionId)
        FROM SubscriptionTrafficDaily d
        WHERE d.bucketStart >= :from AND d.bucketStart < :to
        """
    )
    fun countDistinctSubscriptions(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): Long

    @Query(
        """
        SELECT COUNT(DISTINCT d.subscriptionId)
        FROM SubscriptionTrafficDaily d
        WHERE d.bucketStart >= :from AND d.bucketStart < :to
          AND d.subscriptionId IN :subscriptionIds
        """
    )
    fun countDistinctSubscriptionsByIds(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("subscriptionIds") subscriptionIds: Collection<Int>
    ): Long

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
