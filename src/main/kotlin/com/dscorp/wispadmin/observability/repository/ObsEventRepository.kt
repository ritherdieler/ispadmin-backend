package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ObsEventRepository : JpaRepository<ObsEvent, Long> {

    fun findByIssueIdOrderByCreatedAtDesc(issueId: Long, pageable: Pageable): Page<ObsEvent>

    fun findFirstByIssueIdOrderByCreatedAtDesc(issueId: Long): ObsEvent?

    fun findByCorrelationIdOrderByCreatedAtDesc(correlationId: String): List<ObsEvent>

    fun findBySessionIdOrderByCreatedAtDesc(sessionId: String): List<ObsEvent>

    fun findFirstBySessionIdOrderByCreatedAtDesc(sessionId: String): ObsEvent?

    @Query(
        """
        SELECT e FROM ObsEvent e
        WHERE e.sessionId = :sessionId
          AND (:feature IS NULL OR e.feature = :feature)
          AND (:action IS NULL OR e.action = :action)
        ORDER BY e.createdAt DESC
        """
    )
    fun findSessionEventsFiltered(
        @Param("sessionId") sessionId: String,
        @Param("feature") feature: String?,
        @Param("action") action: String?
    ): List<ObsEvent>

    @Query(
        """
        SELECT e.feature, COUNT(e) FROM ObsEvent e
        WHERE e.createdAt >= :from AND e.feature IS NOT NULL
        GROUP BY e.feature
        ORDER BY COUNT(e) DESC
        """
    )
    fun countGroupedByFeatureSince(
        @Param("from") from: LocalDateTime,
        pageable: Pageable
    ): List<Array<Any>>

    @Query(
        """
        SELECT e.sessionId, e.platform, COUNT(e), MAX(e.createdAt), MIN(e.createdAt)
        FROM ObsEvent e
        WHERE e.sessionId IS NOT NULL
          AND e.createdAt BETWEEN :from AND :to
        GROUP BY e.sessionId, e.platform
        ORDER BY MAX(e.createdAt) DESC
        """,
        countQuery = """
        SELECT COUNT(DISTINCT e.sessionId)
        FROM ObsEvent e
        WHERE e.sessionId IS NOT NULL
          AND e.createdAt BETWEEN :from AND :to
        """
    )
    fun aggregateRecentSessions(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        pageable: Pageable
    ): Page<Array<Any>>

    @Query("SELECT COUNT(e) FROM ObsEvent e WHERE e.createdAt >= :from")
    fun countSince(@Param("from") from: LocalDateTime): Long

    @Query(
        """
        SELECT COUNT(e) FROM ObsEvent e
        WHERE e.createdAt >= :from AND e.createdAt < :to
          AND (:platform IS NULL OR e.platform = :platform)
          AND (:severity IS NULL OR e.severity = :severity)
          AND (:environment IS NULL OR e.environment = :environment)
        """
    )
    fun countInWindow(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
        @Param("platform") platform: String?,
        @Param("severity") severity: String?,
        @Param("environment") environment: String?
    ): Long

    @Query(
        """
        SELECT FUNCTION('DATE_FORMAT', e.createdAt, '%Y-%m-%d %H:00:00'), e.platform, COUNT(e)
        FROM ObsEvent e
        WHERE e.createdAt >= :from AND e.createdAt <= :to
        GROUP BY FUNCTION('DATE_FORMAT', e.createdAt, '%Y-%m-%d %H:00:00'), e.platform
        ORDER BY FUNCTION('DATE_FORMAT', e.createdAt, '%Y-%m-%d %H:00:00')
        """
    )
    fun timeSeriesByPlatform(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime
    ): List<Array<Any>>

    @Modifying
    @Query("DELETE FROM ObsEvent e WHERE e.createdAt < :threshold")
    fun deleteOlderThan(@Param("threshold") threshold: LocalDateTime): Int
}
