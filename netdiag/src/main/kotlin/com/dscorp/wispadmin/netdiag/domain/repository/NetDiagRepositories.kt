package com.dscorp.wispadmin.netdiag.domain.repository

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAlertDecision
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAlertSuppressionWindow
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAuditLog
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagNotificationLog
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagMaintenanceWindow
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagOltLogEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTrapEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

@Repository
interface NetDiagTargetRepository : JpaRepository<NetDiagTarget, Long> {
    fun findByDeviceRefId(deviceRefId: Long): Optional<NetDiagTarget>
    fun findByName(name: String): Optional<NetDiagTarget>
    fun findByEnabledTrue(): List<NetDiagTarget>
    fun findByParentTargetId(parentTargetId: Long): List<NetDiagTarget>
}

@Repository
interface NetDiagProbeRunRepository : JpaRepository<NetDiagProbeRun, Long> {
    fun findTopByTargetIdOrderByStartedAtDesc(targetId: Long): Optional<NetDiagProbeRun>
    fun findTopByTargetIdAndStatusOrderByStartedAtDesc(targetId: Long, status: String): Optional<NetDiagProbeRun>

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM NetDiagProbeRun p WHERE p.startedAt < :cutoff")
    fun deleteByStartedAtBefore(@Param("cutoff") cutoff: Instant): Int
}

@Repository
interface NetDiagIncidentRepository : JpaRepository<NetDiagIncident, Long> {
    fun findTopByDedupKeyAndStatusOrderByOpenedAtDesc(dedupKey: String, status: String): NetDiagIncident?
    fun findByStatusOrderByOpenedAtDesc(status: String): List<NetDiagIncident>
    fun findByDedupKeyAndStatus(dedupKey: String, status: String): Optional<NetDiagIncident>
    fun findByTarget_IdAndStatus(targetId: Long, status: String): List<NetDiagIncident>
    fun findByTarget_IdAndStatusAndReasonCode(
        targetId: Long,
        status: String,
        reasonCode: String
    ): List<NetDiagIncident>
    fun findByTarget_IdInAndStatus(targetIds: Collection<Long>, status: String): List<NetDiagIncident>

    @Query(
        """
        SELECT DISTINCT i FROM NetDiagIncident i
        LEFT JOIN FETCH i.target
        WHERE UPPER(i.status) IN :statuses
          AND (:severity IS NULL OR UPPER(i.severity) = :severity)
          AND (:targetId IS NULL OR i.target.id = :targetId)
          AND (:fromAt IS NULL OR i.openedAt >= :fromAt)
          AND (:toAt IS NULL OR i.openedAt <= :toAt)
        ORDER BY i.openedAt DESC
        """
    )
    fun findForList(
        @Param("statuses") statuses: Collection<String>,
        @Param("severity") severity: String?,
        @Param("targetId") targetId: Long?,
        @Param("fromAt") fromAt: Instant?,
        @Param("toAt") toAt: Instant?
    ): List<NetDiagIncident>

    @Query("SELECT i FROM NetDiagIncident i LEFT JOIN FETCH i.target WHERE i.id = :id")
    fun findByIdWithTarget(@Param("id") id: Long): Optional<NetDiagIncident>

    fun countByStatusIn(statuses: Collection<String>): Long
    fun countBySeverityAndStatusIn(severity: String, statuses: Collection<String>): Long
    fun countByReasonCodeAndStatusIn(reasonCode: String, statuses: Collection<String>): Long

    @Query(
        """
        SELECT i.severity, i.reasonCode, COUNT(i.id)
        FROM NetDiagIncident i
        WHERE UPPER(i.status) IN :statuses
        GROUP BY i.severity, i.reasonCode
        """
    )
    fun summarizeByStatuses(@Param("statuses") statuses: Collection<String>): List<Array<Any?>>
}

@Repository
interface NetDiagIncidentEventRepository : JpaRepository<NetDiagIncidentEvent, Long> {
    fun findByIncidentIdOrderByCreatedAtDesc(incidentId: Long): List<NetDiagIncidentEvent>

    @Query("SELECT e.id FROM NetDiagIncidentEvent e WHERE e.createdAt < :cutoff")
    fun findIdsOlderThan(@Param("cutoff") cutoff: Instant, pageable: Pageable): List<Long>
}

@Repository
interface NetDiagAlertDecisionRepository : JpaRepository<NetDiagAlertDecision, Long> {
    @Query("SELECT d.id FROM NetDiagAlertDecision d WHERE d.createdAt < :cutoff")
    fun findIdsOlderThan(@Param("cutoff") cutoff: Instant, pageable: Pageable): List<Long>
}

@Repository
interface NetDiagAlertSuppressionWindowRepository : JpaRepository<NetDiagAlertSuppressionWindow, Long> {
    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE NetDiagAlertSuppressionWindow w
        SET w.eventCount = w.eventCount + 1, w.lastSeenAt = :seenAt
        WHERE w.incidentId = :incidentId
          AND w.targetId = :targetId
          AND w.reasonCode = :reasonCode
          AND w.windowStart = :windowStart
        """
    )
    fun incrementWindow(
        @Param("incidentId") incidentId: Long,
        @Param("targetId") targetId: Long,
        @Param("reasonCode") reasonCode: String,
        @Param("windowStart") windowStart: Instant,
        @Param("seenAt") seenAt: Instant
    ): Int

    @Query("SELECT w.id FROM NetDiagAlertSuppressionWindow w WHERE w.windowStart < :cutoff")
    fun findIdsOlderThan(@Param("cutoff") cutoff: Instant, pageable: Pageable): List<Long>
}

@Repository
interface NetDiagNotificationLogRepository : JpaRepository<NetDiagNotificationLog, Long> {
    @Query("SELECT n.id FROM NetDiagNotificationLog n WHERE n.createdAt < :cutoff")
    fun findIdsOlderThan(@Param("cutoff") cutoff: Instant, pageable: Pageable): List<Long>
}

@Repository
interface NetDiagAuditLogRepository : JpaRepository<NetDiagAuditLog, Long>

@Repository
interface NetDiagTrapEventRepository : JpaRepository<NetDiagTrapEvent, Long> {
    fun findTop20ByTargetIdOrderByReceivedAtDesc(targetId: Long): List<NetDiagTrapEvent>
}

@Repository
interface NetDiagOltLogEventRepository : JpaRepository<NetDiagOltLogEvent, Long> {
    @Query("select e from NetDiagOltLogEvent e where e.receivedAt > :at or (e.receivedAt = :at and e.id > :id) order by e.receivedAt,e.id")
    fun findChanges(@Param("at") at: Instant,@Param("id") id: Long,page: Pageable): List<NetDiagOltLogEvent>

    fun findTop50ByTargetIdOrderByReceivedAtDesc(targetId: Long): List<NetDiagOltLogEvent>

    fun findTop50ByBoardAndPortOrderByReceivedAtDesc(board: Int, port: Int): List<NetDiagOltLogEvent>

    fun findTop50ByIsUnparsedTrueOrderByReceivedAtDesc(): List<NetDiagOltLogEvent>

    @Query(
        """
        SELECT e FROM NetDiagOltLogEvent e
        WHERE (:board IS NULL OR e.board = :board)
          AND (:port IS NULL OR e.port = :port)
          AND (:unparsedOnly = false OR e.isUnparsed = true)
          AND (:fromAt IS NULL OR e.receivedAt >= :fromAt)
          AND (:toAt IS NULL OR e.receivedAt <= :toAt)
        """
    )
    fun search(
        @Param("board") board: Int?,
        @Param("port") port: Int?,
        @Param("unparsedOnly") unparsedOnly: Boolean,
        @Param("fromAt") fromAt: Instant?,
        @Param("toAt") toAt: Instant?,
        pageable: Pageable
    ): Page<NetDiagOltLogEvent>

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM NetDiagOltLogEvent e WHERE e.receivedAt < :cutoff")
    fun deleteByReceivedAtBefore(@Param("cutoff") cutoff: Instant): Int

    @Query("SELECT e.id FROM NetDiagOltLogEvent e WHERE e.receivedAt < :cutoff")
    fun findIdsOlderThan(@Param("cutoff") cutoff: Instant, pageable: Pageable): List<Long>
}

@Repository
interface NetDiagMaintenanceWindowRepository : JpaRepository<NetDiagMaintenanceWindow, Long> {
    @Query(
        """
        SELECT w FROM NetDiagMaintenanceWindow w
        WHERE w.startsAt <= :at AND w.endsAt >= :at AND w.suppressNotifications = true
        """
    )
    fun findActiveAt(@Param("at") at: Instant): List<NetDiagMaintenanceWindow>
}
