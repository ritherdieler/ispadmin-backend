package com.dscorp.wispadmin.netdiag.domain.repository

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAlertDecision
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAuditLog
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagNotificationLog
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface NetDiagTargetRepository : JpaRepository<NetDiagTarget, Long> {
    fun findByDeviceRefId(deviceRefId: Long): Optional<NetDiagTarget>
    fun findByEnabledTrue(): List<NetDiagTarget>
}

@Repository
interface NetDiagProbeRunRepository : JpaRepository<NetDiagProbeRun, Long> {
    fun findTopByTargetIdOrderByStartedAtDesc(targetId: Long): Optional<NetDiagProbeRun>
}

@Repository
interface NetDiagIncidentRepository : JpaRepository<NetDiagIncident, Long> {
    fun findByStatusOrderByOpenedAtDesc(status: String): List<NetDiagIncident>
    fun findByDedupKeyAndStatus(dedupKey: String, status: String): Optional<NetDiagIncident>
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
