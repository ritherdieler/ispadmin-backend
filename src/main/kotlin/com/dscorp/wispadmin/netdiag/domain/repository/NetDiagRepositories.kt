package com.dscorp.wispadmin.netdiag.domain.repository

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAlertDecision
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAuditLog
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagNotificationLog
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagMaintenanceWindow
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTrapEvent
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
    fun findByEnabledTrue(): List<NetDiagTarget>
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
    fun findByStatusOrderByOpenedAtDesc(status: String): List<NetDiagIncident>
    fun findByDedupKeyAndStatus(dedupKey: String, status: String): Optional<NetDiagIncident>
    fun findByTarget_IdAndStatus(targetId: Long, status: String): List<NetDiagIncident>
    fun existsByTarget_IdAndStatus(targetId: Long, status: String): Boolean
}

@Repository
interface NetDiagIncidentEventRepository : JpaRepository<NetDiagIncidentEvent, Long> {
    fun findByIncidentIdOrderByCreatedAtDesc(incidentId: Long): List<NetDiagIncidentEvent>
}

@Repository
interface NetDiagAlertDecisionRepository : JpaRepository<NetDiagAlertDecision, Long>

@Repository
interface NetDiagNotificationLogRepository : JpaRepository<NetDiagNotificationLog, Long>

@Repository
interface NetDiagAuditLogRepository : JpaRepository<NetDiagAuditLog, Long>

@Repository
interface NetDiagTrapEventRepository : JpaRepository<NetDiagTrapEvent, Long> {
    fun findTop20ByTargetIdOrderByReceivedAtDesc(targetId: Long): List<NetDiagTrapEvent>
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
