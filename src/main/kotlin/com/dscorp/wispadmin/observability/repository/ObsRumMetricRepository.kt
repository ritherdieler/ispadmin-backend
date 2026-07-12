package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsRumMetric
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ObsRumMetricRepository : JpaRepository<ObsRumMetric, Long> {

    fun findByBucketStartBetweenOrderByBucketStartAsc(
        from: LocalDateTime,
        to: LocalDateTime
    ): List<ObsRumMetric>

    @Query(
        """
        SELECT m.page, m.metricName,
               SUM(m.sampleCount), SUM(m.goodCount), SUM(m.needsImprovementCount), SUM(m.poorCount),
               AVG(m.avg), MAX(m.p75), MAX(m.p95), MAX(m.p99), MAX(m.max)
        FROM ObsRumMetric m
        WHERE m.bucketStart >= :from AND m.bucketStart <= :to
        GROUP BY m.page, m.metricName
        ORDER BY SUM(m.sampleCount) DESC
        """
    )
    fun aggregateByPageAndMetric(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<Array<Any>>

    @Modifying
    @Query("DELETE FROM ObsRumMetric m WHERE m.bucketStart < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDateTime): Int
}
