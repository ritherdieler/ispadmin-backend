package com.dscorp.wispadmin.oltgateway.domain.repository

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrCustomTemplate
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltModel
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltPonPort
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltVlan
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuExtraVlan
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuExtraVlanId
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuServicePort
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuType
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSpeedProfile
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSyncRun
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrTask
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface OltMgrOltRepository : JpaRepository<OltMgrOlt, Long> {
    fun findByName(name: String): Optional<OltMgrOlt>
    fun findByIpAddress(ipAddress: String): Optional<OltMgrOlt>
}

@Repository
interface OltMgrOltModelRepository : JpaRepository<OltMgrOltModel, Long> {
    fun findByCode(code: String): Optional<OltMgrOltModel>
}

@Repository
interface OltMgrZoneRepository : JpaRepository<OltMgrZone, Long> {
    fun findByName(name: String): Optional<OltMgrZone>
}

@Repository
interface OltMgrOnuTypeRepository : JpaRepository<OltMgrOnuType, Long> {
    fun findByName(name: String): Optional<OltMgrOnuType>
}

@Repository
interface OltMgrOnuRepository : JpaRepository<OltMgrOnu, Long> {
    fun findBySnAndDeletedAtIsNull(sn: String): Optional<OltMgrOnu>
    fun findByExternalIdAndDeletedAtIsNull(externalId: String): Optional<OltMgrOnu>
    fun findBySn(sn: String): Optional<OltMgrOnu>
    fun findByExternalId(externalId: String): Optional<OltMgrOnu>
    fun findByOlt_Id(oltId: Long): List<OltMgrOnu>

    @EntityGraph(attributePaths = ["status"])
    fun findByDeletedAtIsNull(pageable: Pageable): Page<OltMgrOnu>

    @Query(
        "select coalesce(max(o.onuIndex), -1) from OltMgrOnu o " +
            "where o.olt.id = :oltId and o.board = :board and o.port = :port and o.deletedAt is null"
    )
    fun findMaxOnuIndex(
        @Param("oltId") oltId: Long,
        @Param("board") board: Int,
        @Param("port") port: Int
    ): Int
}

@Repository
interface OltMgrOnuStatusCurrentRepository : JpaRepository<OltMgrOnuStatusCurrent, Long>

@Repository
interface OltMgrTaskRepository : JpaRepository<OltMgrTask, Long> {
    fun existsByStatus(status: String): Boolean
}

@Repository
interface OltMgrSyncRunRepository : JpaRepository<OltMgrSyncRun, Long> {
    fun findTopByOrderByStartedAtDesc(): Optional<OltMgrSyncRun>
}

@Repository
interface OltMgrAuditLogRepository : JpaRepository<OltMgrAuditLog, Long>

@Repository
interface OltMgrOnuServicePortRepository : JpaRepository<OltMgrOnuServicePort, Long>

@Repository
interface OltMgrOnuExtraVlanRepository : JpaRepository<OltMgrOnuExtraVlan, OltMgrOnuExtraVlanId>

@Repository
interface OltMgrCustomTemplateRepository : JpaRepository<OltMgrCustomTemplate, Long>

@Repository
interface OltMgrSpeedProfileRepository : JpaRepository<OltMgrSpeedProfile, Long>

@Repository
interface OltMgrOltPonPortRepository : JpaRepository<OltMgrOltPonPort, Long>

@Repository
interface OltMgrOltVlanRepository : JpaRepository<OltMgrOltVlan, Long>
