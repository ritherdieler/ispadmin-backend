package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsEndpointMetric
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ObsEndpointMetricRepository : JpaRepository<ObsEndpointMetric, Long> {

    fun findByBucketStartBetweenOrderByBucketStartAsc(
        from: LocalDateTime,
        to: LocalDateTime
    ): List<ObsEndpointMetric>

    @Query(
        """
        SELECT m.route, m.httpMethod,
               SUM(m.sampleCount), SUM(m.errorCount),
               AVG(m.avgMs), MAX(m.p95Ms), MAX(m.p99Ms), MAX(m.maxMs)
        FROM ObsEndpointMetric m
        WHERE m.bucketStart >= :from AND m.bucketStart <= :to
        GROUP BY m.route, m.httpMethod
        ORDER BY MAX(m.p95Ms) DESC
        """
    )
    fun aggregateByRoute(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<Array<Any>>

    @Modifying
    @Query("DELETE FROM ObsEndpointMetric m WHERE m.bucketStart < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDateTime): Int
}
