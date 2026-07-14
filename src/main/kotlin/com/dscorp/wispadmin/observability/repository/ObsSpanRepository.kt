package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsSpan
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ObsSpanRepository : JpaRepository<ObsSpan, Long> {

    fun findByTraceIdOrderByStartEpochMsAsc(traceId: String): List<ObsSpan>

    @Query(
        """
        SELECT s FROM ObsSpan s
        WHERE s.parentSpanId IS NULL
          AND (:from IS NULL OR s.startEpochMs >= :from)
          AND (:to IS NULL OR s.startEpochMs <= :to)
          AND (:route IS NULL OR s.httpRoute = :route)
          AND (:minDurationMs IS NULL OR s.durationMs >= :minDurationMs)
          AND (:status IS NULL OR s.status = :status)
          AND (:platform IS NULL OR s.platform = :platform)
          AND (:sessionId IS NULL OR s.sessionId = :sessionId)
          AND (:release IS NULL OR s.release = :release)
        ORDER BY s.startEpochMs DESC
        """
    )
    fun searchRootSpans(
        @Param("from") from: Long?,
        @Param("to") to: Long?,
        @Param("route") route: String?,
        @Param("minDurationMs") minDurationMs: Long?,
        @Param("status") status: String?,
        @Param("platform") platform: String?,
        @Param("sessionId") sessionId: String?,
        @Param("release") release: String?,
        pageable: Pageable
    ): Page<ObsSpan>

    @Query("SELECT s.traceId, COUNT(s), SUM(CASE WHEN s.status = 'ERROR' THEN 1 ELSE 0 END) FROM ObsSpan s WHERE s.traceId IN :traceIds GROUP BY s.traceId")
    fun aggregateByTraceIds(@Param("traceIds") traceIds: List<String>): List<Array<Any>>

    @Query(
        """
        SELECT s.dbStatement, COUNT(s), SUM(s.durationMs), AVG(s.durationMs), MAX(s.durationMs)
        FROM ObsSpan s
        WHERE s.kind = 'DB'
          AND s.dbStatement IS NOT NULL
          AND (:from IS NULL OR s.startEpochMs >= :from)
          AND (:to IS NULL OR s.startEpochMs <= :to)
          AND (:release IS NULL OR s.release = :release)
        GROUP BY s.dbStatement
        ORDER BY SUM(s.durationMs) DESC
        """
    )
    fun aggregateDbStatements(
        @Param("from") from: Long?,
        @Param("to") to: Long?,
        @Param("release") release: String?,
        pageable: Pageable
    ): List<Array<Any>>

    @Query(
        """
        SELECT s.traceId, s.dbStatement, COUNT(s), SUM(s.durationMs), MAX(root.httpRoute)
        FROM ObsSpan s, ObsSpan root
        WHERE s.kind = 'DB'
          AND s.dbStatement IS NOT NULL
          AND root.parentSpanId IS NULL
          AND root.traceId = s.traceId
          AND (:from IS NULL OR s.startEpochMs >= :from)
          AND (:to IS NULL OR s.startEpochMs <= :to)
          AND (:release IS NULL OR s.release = :release)
        GROUP BY s.traceId, s.dbStatement
        HAVING COUNT(s) >= :threshold
        ORDER BY COUNT(s) DESC
        """
    )
    fun findNPlusOneCandidates(
        @Param("from") from: Long?,
        @Param("to") to: Long?,
        @Param("threshold") threshold: Long,
        @Param("release") release: String?
    ): List<Array<Any>>

    @Query(
        """
        SELECT s.sessionId, COUNT(s)
        FROM ObsSpan s
        WHERE s.parentSpanId IS NULL
          AND s.sessionId IN :sessionIds
        GROUP BY s.sessionId
        """
    )
    fun countRootSpansBySession(@Param("sessionIds") sessionIds: List<String>): List<Array<Any>>

    @Query(
        """
        SELECT COUNT(s), SUM(CASE WHEN s.status = 'ERROR' THEN 1 ELSE 0 END)
        FROM ObsSpan s
        WHERE s.parentSpanId IS NULL
          AND s.startEpochMs >= :from
        """
    )
    fun aggregateRootSpansSince(@Param("from") from: Long): List<Array<Any>>

    @Query(
        """
        SELECT COUNT(s), SUM(CASE WHEN s.status = 'ERROR' THEN 1 ELSE 0 END)
        FROM ObsSpan s
        WHERE s.parentSpanId IS NULL
          AND s.startEpochMs >= :from
          AND s.startEpochMs < :to
          AND (:platform IS NULL OR s.platform = :platform)
        """
    )
    fun aggregateRootSpansBetween(
        @Param("from") from: Long,
        @Param("to") to: Long,
        @Param("platform") platform: String?
    ): List<Array<Any>>

    @Query(
        """
        SELECT s.durationMs
        FROM ObsSpan s
        WHERE s.parentSpanId IS NULL
          AND s.startEpochMs >= :from
          AND s.durationMs IS NOT NULL
        ORDER BY s.durationMs ASC
        """
    )
    fun rootSpanDurationsSince(@Param("from") from: Long): List<Long>

    @Query(
        """
        SELECT s FROM ObsSpan s
        WHERE s.parentSpanId IS NULL
          AND s.startEpochMs >= :from
        ORDER BY s.durationMs DESC
        """
    )
    fun findSlowestRootSpansSince(@Param("from") from: Long, pageable: Pageable): List<ObsSpan>

    @Query(
        """
        SELECT root.httpRoute, SUM(db.durationMs)
        FROM ObsSpan db, ObsSpan root
        WHERE db.kind = 'DB'
          AND root.parentSpanId IS NULL
          AND db.traceId = root.traceId
          AND root.httpRoute IS NOT NULL
          AND root.startEpochMs >= :from
          AND root.startEpochMs <= :to
          AND (:release IS NULL OR root.release = :release)
        GROUP BY root.httpRoute
        """
    )
    fun aggregateDbTimeByRoute(
        @Param("from") from: Long,
        @Param("to") to: Long,
        @Param("release") release: String?
    ): List<Array<Any>>

    @Query(
        """
        SELECT s.httpRoute, s.httpMethod, s.durationMs, s.status
        FROM ObsSpan s
        WHERE s.parentSpanId IS NULL
          AND s.httpRoute IS NOT NULL
          AND s.durationMs IS NOT NULL
          AND s.startEpochMs >= :from
          AND s.startEpochMs <= :to
          AND (:release IS NULL OR s.release = :release)
        """
    )
    fun rootSpanMetricsByRoute(
        @Param("from") from: Long,
        @Param("to") to: Long,
        @Param("release") release: String?
    ): List<Array<Any>>

    @Modifying
    @Query("DELETE FROM ObsSpan s WHERE s.startEpochMs < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: Long): Int
}
