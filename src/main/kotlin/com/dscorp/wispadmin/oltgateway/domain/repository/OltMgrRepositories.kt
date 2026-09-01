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

    @EntityGraph(attributePaths = ["status", "olt", "zone", "onuType"])
    fun findByExternalIdAndDeletedAtIsNull(externalId: String): Optional<OltMgrOnu>
    fun findBySn(sn: String): Optional<OltMgrOnu>
    fun findByExternalId(externalId: String): Optional<OltMgrOnu>
    fun findByOlt_Id(oltId: Long): List<OltMgrOnu>

    @Query("SELECT o FROM OltMgrOnu o WHERE UPPER(o.sn) = UPPER(:sn) AND o.deletedAt IS NULL")
    fun findBySnIgnoreCaseAndDeletedAtIsNull(@Param("sn") sn: String): Optional<OltMgrOnu>

    @Query(
        "SELECT o FROM OltMgrOnu o WHERE o.deletedAt IS NULL AND UPPER(o.sn) LIKE CONCAT('%', UPPER(:suffix))"
    )
    fun findBySnSuffixIgnoreCaseAndDeletedAtIsNull(@Param("suffix") suffix: String): List<OltMgrOnu>

    @Query(
        "SELECT o FROM OltMgrOnu o WHERE o.olt.id = :oltId AND o.board = :board " +
            "AND o.port = :port AND o.onuIndex = :onuIndex AND o.deletedAt IS NULL"
    )
    fun findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(
        @Param("oltId") oltId: Long,
        @Param("board") board: Int,
        @Param("port") port: Int,
        @Param("onuIndex") onuIndex: Int
    ): Optional<OltMgrOnu>

    @EntityGraph(attributePaths = ["status"])
    @Query("SELECT o FROM OltMgrOnu o WHERE o.olt.id = :oltId")
    fun findByOlt_IdWithStatus(@Param("oltId") oltId: Long): List<OltMgrOnu>

    @EntityGraph(attributePaths = ["status"])
    @Query(
        "SELECT o FROM OltMgrOnu o WHERE o.olt.id = :oltId AND o.board = :board " +
            "AND o.port = :port AND o.deletedAt IS NULL"
    )
    fun findByOlt_IdAndBoardAndPortWithStatus(
        @Param("oltId") oltId: Long,
        @Param("board") board: Int,
        @Param("port") port: Int
    ): List<OltMgrOnu>

    @EntityGraph(attributePaths = ["status"])
    fun findByDeletedAtIsNull(pageable: Pageable): Page<OltMgrOnu>

    @EntityGraph(attributePaths = ["status", "olt", "zone", "onuType"])
    @Query(
        """
        SELECT o FROM OltMgrOnu o LEFT JOIN o.status s
        WHERE o.deletedAt IS NULL
          AND (:q IS NULL OR LOWER(o.sn) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
               OR LOWER(COALESCE(o.name, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
               OR LOWER(COALESCE(o.ipAddress, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
               OR LOWER(COALESCE(o.address, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
               OR LOWER(COALESCE(o.contact, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
          AND (:board IS NULL OR o.board = :board)
          AND (:port IS NULL OR o.port = :port)
          AND (:oltId IS NULL OR o.olt.id = :oltId)
          AND (:zoneId IS NULL OR o.zone.id = :zoneId)
          AND (:vlan IS NULL OR o.mainVlanId = :vlan)
          AND (:onuTypeId IS NULL OR o.onuType.id = :onuTypeId)
          AND (:onuTypeName IS NULL OR LOWER(COALESCE(o.onuTypeName, '')) = LOWER(CAST(:onuTypeName AS string)))
          AND (:customProfile IS NULL OR LOWER(COALESCE(o.customProfile, '')) = LOWER(CAST(:customProfile AS string)))
          AND (:ponType IS NULL OR LOWER(o.ponType) = LOWER(CAST(:ponType AS string)))
          AND (:mode IS NULL OR LOWER(COALESCE(o.mode, '')) = LOWER(CAST(:mode AS string)))
          AND (:runState IS NULL OR LOWER(s.runState) = LOWER(CAST(:runState AS string)))
          AND (:signalCategory IS NULL OR LOWER(COALESCE(s.signalCategory, '')) = LOWER(CAST(:signalCategory AS string)))
          AND (:splitterId IS NULL OR o.splitterId = :splitterId)
          AND (:configurationMethod IS NULL OR LOWER(COALESCE(o.configurationMethod, '')) = LOWER(CAST(:configurationMethod AS string)))
          AND (:wanMode IS NULL OR LOWER(COALESCE(o.wanMode, '')) = LOWER(CAST(:wanMode AS string)))
          AND (:mgmtIpMode IS NULL OR LOWER(COALESCE(o.mgmtIpMode, '')) = LOWER(CAST(:mgmtIpMode AS string)))
          AND (:importedSynced IS NULL OR o.syncedAfterImport = :importedSynced)
          AND (:lastResyncFailed IS NULL OR o.lastResyncFailed = :lastResyncFailed)
          AND (:lineProfileMaptype IS NULL OR LOWER(COALESCE(o.lineProfileMaptype, '')) = LOWER(CAST(:lineProfileMaptype AS string)))
          AND (:administrativeStatus IS NULL OR LOWER(o.administrativeStatus) = LOWER(CAST(:administrativeStatus AS string)))
          AND (:lastDownCause IS NULL OR LOWER(COALESCE(s.lastDownCause, '')) LIKE LOWER(CONCAT('%', CAST(:lastDownCause AS string), '%')))
        """
    )
    fun findConfiguredFiltered(
        @Param("q") q: String?,
        @Param("board") board: Int?,
        @Param("port") port: Int?,
        @Param("oltId") oltId: Long?,
        @Param("zoneId") zoneId: Long?,
        @Param("vlan") vlan: Int?,
        @Param("onuTypeId") onuTypeId: Long?,
        @Param("onuTypeName") onuTypeName: String?,
        @Param("customProfile") customProfile: String?,
        @Param("ponType") ponType: String?,
        @Param("mode") mode: String?,
        @Param("runState") runState: String?,
        @Param("signalCategory") signalCategory: String?,
        @Param("splitterId") splitterId: Long?,
        @Param("configurationMethod") configurationMethod: String?,
        @Param("wanMode") wanMode: String?,
        @Param("mgmtIpMode") mgmtIpMode: String?,
        @Param("importedSynced") importedSynced: Boolean?,
        @Param("lastResyncFailed") lastResyncFailed: Boolean?,
        @Param("lineProfileMaptype") lineProfileMaptype: String?,
        @Param("administrativeStatus") administrativeStatus: String?,
        @Param("lastDownCause") lastDownCause: String?,
        pageable: Pageable
    ): Page<OltMgrOnu>

    @Query(
        "SELECT DISTINCT o.mainVlanId FROM OltMgrOnu o WHERE o.deletedAt IS NULL AND o.mainVlanId IS NOT NULL ORDER BY o.mainVlanId"
    )
    fun findDistinctVlans(): List<Int>

    @Query(
        "SELECT DISTINCT o.customProfile FROM OltMgrOnu o WHERE o.deletedAt IS NULL AND o.customProfile IS NOT NULL AND o.customProfile <> '' ORDER BY o.customProfile"
    )
    fun findDistinctProfiles(): List<String>

    @Query(
        "SELECT DISTINCT o.ponType FROM OltMgrOnu o WHERE o.deletedAt IS NULL AND o.ponType IS NOT NULL ORDER BY o.ponType"
    )
    fun findDistinctPonTypes(): List<String>

    @Query(
        "SELECT DISTINCT o.splitterId FROM OltMgrOnu o WHERE o.deletedAt IS NULL AND o.splitterId IS NOT NULL ORDER BY o.splitterId"
    )
    fun findDistinctSplitterIds(): List<Long>

    @Query(
        "SELECT DISTINCT o.board FROM OltMgrOnu o WHERE o.deletedAt IS NULL AND (:oltId IS NULL OR o.olt.id = :oltId) ORDER BY o.board"
    )
    fun findDistinctBoards(@Param("oltId") oltId: Long?): List<Int>

    @Query(
        "SELECT DISTINCT o.port FROM OltMgrOnu o WHERE o.deletedAt IS NULL AND (:oltId IS NULL OR o.olt.id = :oltId) AND (:board IS NULL OR o.board = :board) ORDER BY o.port"
    )
    fun findDistinctPorts(@Param("oltId") oltId: Long?, @Param("board") board: Int?): List<Int>

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
interface OltMgrAuditLogRepository : JpaRepository<OltMgrAuditLog, Long> {
    fun findByOnu_IdOrderByCreatedAtDesc(onuId: Long, pageable: Pageable): List<OltMgrAuditLog>
}

@Repository
interface OltMgrOnuServicePortRepository : JpaRepository<OltMgrOnuServicePort, Long> {
    @EntityGraph(attributePaths = ["downloadSpeed", "uploadSpeed"])
    fun findByOnu_Id(onuId: Long): List<OltMgrOnuServicePort>
}

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
