package com.dscorp.wispadmin.traffic.repository

import com.dscorp.wispadmin.traffic.entity.NetworkTrafficHourOfDay
import com.dscorp.wispadmin.traffic.entity.NetworkTrafficHourOfDayId
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficCounterState
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficDaily
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficHourly
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficMonthly
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
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
