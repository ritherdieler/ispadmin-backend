package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsIssue
import com.dscorp.wispadmin.observability.entity.ObsIssueStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ObsIssueRepository : JpaRepository<ObsIssue, Long> {

    fun findByFingerprint(fingerprint: String): ObsIssue?

    fun findByTrackerIssueKey(trackerIssueKey: String): ObsIssue?

    fun findByJiraIssueKey(jiraIssueKey: String): ObsIssue?

    fun findByTrackerIssueKeyIsNullAndJiraIssueKeyIsNotNull(): List<ObsIssue>

    @Query(
        """
        SELECT i FROM ObsIssue i
        WHERE (:platform IS NULL OR i.platform = :platform)
          AND (:severity IS NULL OR i.severity = :severity)
          AND (:status IS NULL OR i.status = :status)
          AND (:environment IS NULL OR i.lastEnvironment = :environment)
          AND (:release IS NULL OR i.lastRelease = :release)
          AND (:from IS NULL OR i.lastSeen >= :from)
          AND (:to IS NULL OR i.lastSeen <= :to)
          AND (:text IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :text, '%'))
               OR LOWER(i.lastMessage) LIKE LOWER(CONCAT('%', :text, '%'))
               OR LOWER(i.errorType) LIKE LOWER(CONCAT('%', :text, '%')))
        """
    )
    fun search(
        @Param("platform") platform: String?,
        @Param("severity") severity: String?,
        @Param("status") status: ObsIssueStatus?,
        @Param("environment") environment: String?,
        @Param("release") release: String?,
        @Param("from") from: LocalDateTime?,
        @Param("to") to: LocalDateTime?,
        @Param("text") text: String?,
        pageable: Pageable
    ): Page<ObsIssue>

    @Query(
        """
        SELECT i FROM ObsIssue i
        WHERE i.lastRelease = :release
          AND (:since IS NULL OR i.firstSeen >= :since)
        ORDER BY i.firstSeen DESC
        """
    )
    fun findNewIssuesForRelease(
        @Param("release") release: String,
        @Param("since") since: LocalDateTime?,
        pageable: Pageable
    ): List<ObsIssue>

    @Query(
        """
        SELECT COUNT(i) FROM ObsIssue i
        WHERE i.lastRelease = :release
          AND (:since IS NULL OR i.firstSeen >= :since)
        """
    )
    fun countNewIssuesForRelease(
        @Param("release") release: String,
        @Param("since") since: LocalDateTime?
    ): Long

    @Query(
        """
        SELECT DISTINCT i FROM ObsIssue i, ObsEvent e
        WHERE e.issueId = i.id
          AND e.url LIKE CONCAT('%', :route, '%')
          AND (:from IS NULL OR e.createdAt >= :from)
          AND (:to IS NULL OR e.createdAt <= :to)
        ORDER BY i.lastSeen DESC
        """
    )
    fun findIssuesByRoute(
        @Param("route") route: String,
        @Param("from") from: LocalDateTime?,
        @Param("to") to: LocalDateTime?,
        pageable: Pageable
    ): List<ObsIssue>

    @Query("SELECT COUNT(i) FROM ObsIssue i WHERE i.status = :status")
    fun countByStatus(@Param("status") status: ObsIssueStatus): Long

    @Query("SELECT i.platform, COUNT(i) FROM ObsIssue i GROUP BY i.platform")
    fun countGroupedByPlatform(): List<Array<Any>>

    @Query("SELECT i.severity, COUNT(i) FROM ObsIssue i WHERE i.status = 'OPEN' GROUP BY i.severity")
    fun countOpenGroupedBySeverity(): List<Array<Any>>

    @Query("SELECT i FROM ObsIssue i WHERE i.status = 'OPEN' ORDER BY i.eventCount DESC")
    fun findTopOpenIssues(pageable: Pageable): List<ObsIssue>

    fun deleteByStatusInAndLastSeenBefore(statuses: List<ObsIssueStatus>, threshold: LocalDateTime): Long
}
