package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.MoveOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuType
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrTask
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuTypeRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrZoneRepository
import com.dscorp.wispadmin.oltgateway.exception.OltGatewayConflictException
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import com.dscorp.wispadmin.oltgateway.mapper.SmartOltCompatMapper
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class AppliedAuthorization(
    val board: Int?,
    val port: Int?,
    val ontId: Int?,
    val externalId: String?
)

data class AuthorizePlan(
    val sn: String,
    val board: Int,
    val port: Int,
    val ontId: Int,
    val externalId: String,
    val commands: List<String>,
    val alreadyAuthorized: Boolean
)

open class OltManagerFacade(
    private val oltRepository: OltMgrOltRepository,
    private val zoneRepository: OltMgrZoneRepository,
    private val onuTypeRepository: OltMgrOnuTypeRepository,
    private val onuRepository: OltMgrOnuRepository,
    private val statusRepository: OltMgrOnuStatusCurrentRepository,
    private val taskRepository: OltMgrTaskRepository,
    private val auditLogRepository: OltMgrAuditLogRepository,
    private val commandService: OltGatewayCommandService,
    private val queryFacade: OltGatewayQueryFacade,
    private val mapper: SmartOltCompatMapper,
    private val properties: OltGatewayProperties
) {

    @Transactional(readOnly = true)
    open fun unconfiguredOnus(): SmartOltUnconfiguredOnusResponseDto {
        val pending = queryFacade.autofindParsed().filter { parsed ->
            val sn = HuaweiGponSnmpCodec.normalizeOntSn(parsed.sn)
            sn.isNotBlank() && onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(sn).isEmpty
        }
        return mapper.toUnconfirmedOnuResponse(pending, properties.oltId)
    }

    @Transactional
    open fun getOnusDetailsBySn(sn: String): SmartOltOnuBySnResponseDto {
        findExistingBySn(sn)?.let { return responseFromDb(it) }

        val parsed = queryFacade.bySnParsed(sn)
            ?: throw OnuNotFoundException("ONU not found for SN=$sn")

        val olt = requireOlt()
        findExistingByPosition(olt.id!!, parsed.slot, parsed.port, parsed.ontId)?.let {
            return responseFromDb(it)
        }

        val imported = OltMgrOnu(
            sn = parsed.sn,
            externalId = OnuExternalIdPolicy.canonical(properties.oltId, parsed.slot, parsed.port, parsed.ontId),
            olt = olt,
            board = parsed.slot,
            port = parsed.port,
            onuIndex = parsed.ontId,
            name = parsed.description,
            lineProfileName = parsed.lineProfileName,
            serviceProfileName = parsed.serviceProfileName,
            importedFromOlt = true,
            syncedAfterImport = false,
            authorizationDate = Instant.now()
        )
        val saved = onuRepository.save(imported)
        val status = OltMgrOnuStatusCurrent(
            onu = saved,
            runState = parsed.runState ?: "offline",
            matchState = null,
            polledAt = Instant.now()
        )
        saved.status = status
        statusRepository.save(status)
        return mapper.toOnuBySnResponse(parsed, properties.oltId)
    }

    @Transactional(readOnly = true)
    open fun findSnByExternalId(externalId: String): String? =
        onuRepository.findByExternalIdAndDeletedAtIsNull(externalId).map { it.sn }.orElse(null)

    @Transactional(readOnly = true)
    open fun planAuthorize(request: AuthorizeOnuFormDto): AuthorizePlan {
        val olt = requireOlt()
        val board = request.board.toInt()
        val port = request.port.toInt()
        val vlan = request.vlan.toIntOrNull() ?: 0
        val existing = onuRepository.findBySnAndDeletedAtIsNull(request.sn)
        val ontId = existing.map { it.onuIndex }
            .orElseGet { onuRepository.findMaxOnuIndex(olt.id!!, board, port) + 1 }
        val commands = commandService.planAuthorize(
            AuthorizeCliRequest(
                board = board,
                port = port,
                ontId = ontId,
                sn = request.sn,
                lineProfileId = properties.writes.defaultLineProfileId,
                serviceProfileId = properties.writes.defaultServiceProfileId,
                description = request.name.ifBlank { request.sn },
                vlan = vlan
            )
        )
        return AuthorizePlan(
            sn = request.sn,
            board = board,
            port = port,
            ontId = ontId,
            externalId = OnuExternalIdPolicy.canonical(properties.oltId, board, port, ontId),
            commands = commands,
            alreadyAuthorized = existing.isPresent
        )
    }

    @Transactional
    open fun recordAuthorizeShadow(request: AuthorizeOnuFormDto, applied: AppliedAuthorization?): Boolean {
        val plan = try {
            planAuthorize(request)
        } catch (ex: Exception) {
            auditLogRepository.save(
                OltMgrAuditLog(
                    olt = oltRepository.findByName(properties.oltId).orElse(null),
                    action = "authorize_shadow",
                    source = "shadow",
                    details = """{"sn":"${request.sn}","error":"${ex.message?.take(200)}"}"""
                )
            )
            return false
        }

        val diverges = applied == null ||
            applied.board != plan.board ||
            applied.port != plan.port ||
            applied.ontId != plan.ontId
        auditLogRepository.save(
            OltMgrAuditLog(
                olt = requireOlt(),
                action = "authorize_shadow",
                source = "shadow",
                details = """{"sn":"${plan.sn}",""" +
                    """"applied":{"board":${applied?.board},"port":${applied?.port},""" +
                    """"ont_id":${applied?.ontId},"external_id":"${applied?.externalId.orEmpty()}"},""" +
                    """"gateway":{"board":${plan.board},"port":${plan.port},""" +
                    """"ont_id":${plan.ontId},"external_id":"${plan.externalId}"},""" +
                    """"already_authorized":${plan.alreadyAuthorized},"diverges":$diverges,""" +
                    """"commands":${plan.commands.size}}"""
            )
        )
        return diverges
    }

    @Transactional
    open fun authorizeOnu(request: AuthorizeOnuFormDto): SmartOltActionResponseDto {
        if (onuRepository.findBySnAndDeletedAtIsNull(request.sn).isPresent) {
            throw OltGatewayConflictException("ONU already authorized for SN=${request.sn}")
        }
        releaseSoftDeletedSn(request.sn)
        val olt = requireOlt()
        val board = request.board.toInt()
        val port = request.port.toInt()
        val vlan = request.vlan.toIntOrNull() ?: 0
        val nextOntId = onuRepository.findMaxOnuIndex(olt.id!!, board, port) + 1
        val zone = resolveZone(request.zone)
        val onuType = resolveOnuType(request.onu_type)

        val task = taskRepository.save(
            OltMgrTask(
                olt = olt,
                type = "authorize",
                payload = """{"sn":"${request.sn}","board":$board,"port":$port}""",
                status = "running",
                source = "api",
                startedAt = Instant.now()
            )
        )

        try {
            commandService.authorize(
                AuthorizeCliRequest(
                    board = board,
                    port = port,
                    ontId = nextOntId,
                    sn = request.sn,
                    lineProfileId = properties.writes.defaultLineProfileId,
                    serviceProfileId = properties.writes.defaultServiceProfileId,
                    description = request.name.ifBlank { request.sn },
                    vlan = vlan
                )
            )

            val externalId = OnuExternalIdPolicy.canonical(properties.oltId, board, port, nextOntId)
            val onu = onuRepository.save(
                OltMgrOnu(
                    sn = request.sn,
                    externalId = externalId,
                    olt = olt,
                    board = board,
                    port = port,
                    onuIndex = nextOntId,
                    ponType = request.pon_type.ifBlank { "gpon" },
                    onuType = onuType,
                    onuTypeName = request.onu_type,
                    zone = zone,
                    zoneName = request.zone,
                    name = request.name,
                    mode = request.onu_mode.ifBlank { "routing" },
                    mainVlanId = vlan,
                    customProfile = request.custom_profile,
                    authorizationDate = Instant.now(),
                    administrativeStatus = "enabled",
                    importedFromOlt = false,
                    syncedAfterImport = true
                )
            )
            val status = OltMgrOnuStatusCurrent(onu = onu, runState = "offline", polledAt = Instant.now())
            onu.status = status
            statusRepository.save(status)
            task.onu = onu
            task.status = "success"
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            auditLogRepository.save(
                OltMgrAuditLog(
                    olt = olt,
                    onu = onu,
                    action = "authorize_onu",
                    source = "api",
                    details = """{"external_id":"$externalId"}"""
                )
            )
            return SmartOltActionResponseDto(status = true, unique_external_id = externalId)
        } catch (ex: Exception) {
            task.status = "failed"
            task.error = ex.message
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            throw ex
        }
    }

    @Transactional
    open fun moveOnu(sn: String, request: MoveOnuFormDto): SmartOltActionResponseDto {
        val onu = onuRepository.findBySnAndDeletedAtIsNull(sn)
            .orElseThrow { OnuNotFoundException("ONU not found for SN=$sn") }
        val toBoard = request.board.toInt()
        val toPort = request.port.toInt()
        val olt = requireOlt()
        val task = taskRepository.save(
            OltMgrTask(
                olt = olt,
                onu = onu,
                type = "move",
                payload = """{"sn":"$sn","to_board":$toBoard,"to_port":$toPort}""",
                status = "running",
                source = "api",
                startedAt = Instant.now()
            )
        )
        try {
            commandService.move(
                MoveCliRequest(
                    fromBoard = onu.board,
                    fromPort = onu.port,
                    fromOntId = onu.onuIndex,
                    toBoard = toBoard,
                    toPort = toPort,
                    toOntId = onu.onuIndex,
                    sn = onu.sn,
                    lineProfileId = properties.writes.defaultLineProfileId,
                    serviceProfileId = properties.writes.defaultServiceProfileId,
                    description = onu.name ?: onu.sn,
                    vlan = onu.mainVlanId ?: 0
                )
            )
            onu.board = toBoard
            onu.port = toPort
            onu.updatedAt = Instant.now()
            onuRepository.save(onu)
            task.status = "success"
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            auditLogRepository.save(
                OltMgrAuditLog(
                    olt = olt,
                    onu = onu,
                    action = "move_onu",
                    source = "api",
                    details = """{"external_id":"${onu.externalId}"}"""
                )
            )
            return SmartOltActionResponseDto(status = true, unique_external_id = onu.externalId)
        } catch (ex: Exception) {
            task.status = "failed"
            task.error = ex.message
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            throw ex
        }
    }

    @Transactional
    open fun deleteOnu(externalId: String): SmartOltActionResponseDto {
        val onu = onuRepository.findByExternalIdAndDeletedAtIsNull(externalId)
            .orElseThrow { OnuNotFoundException("ONU not found for externalId=$externalId") }
        val olt = requireOlt()
        val task = taskRepository.save(
            OltMgrTask(
                olt = olt,
                onu = onu,
                type = "delete",
                payload = """{"external_id":"$externalId"}""",
                status = "running",
                source = "api",
                startedAt = Instant.now()
            )
        )
        try {
            commandService.delete(DeleteCliRequest(board = onu.board, port = onu.port, ontId = onu.onuIndex))
            onu.sn = tombstoneSn(onu.sn, onu.id)
            onu.deletedAt = Instant.now()
            onu.updatedAt = Instant.now()
            onu.board = -1
            onu.port = 0
            onu.onuIndex = (onu.id ?: 0L).toInt().coerceAtLeast(0)
            onu.externalId = "${properties.oltId}_deleted_${onu.id}"
            onuRepository.save(onu)
            task.status = "success"
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            auditLogRepository.save(
                OltMgrAuditLog(
                    olt = olt,
                    onu = onu,
                    action = "delete_onu",
                    source = "api",
                    details = """{"external_id":"$externalId"}"""
                )
            )
            return SmartOltActionResponseDto(status = true, unique_external_id = externalId)
        } catch (ex: Exception) {
            task.status = "failed"
            task.error = ex.message
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            throw ex
        }
    }

    @Transactional
    open fun rebootOnu(externalId: String): SmartOltActionResponseDto {
        val onu = onuRepository.findByExternalIdAndDeletedAtIsNull(externalId)
            .orElseThrow { OnuNotFoundException("ONU not found for externalId=$externalId") }
        val olt = requireOlt()
        val task = taskRepository.save(
            OltMgrTask(
                olt = olt,
                onu = onu,
                type = "reboot",
                payload = """{"external_id":"$externalId"}""",
                status = "running",
                source = "api",
                startedAt = Instant.now()
            )
        )
        try {
            commandService.reboot(RebootCliRequest(board = onu.board, port = onu.port, ontId = onu.onuIndex))
            task.status = "success"
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            auditLogRepository.save(
                OltMgrAuditLog(
                    olt = olt,
                    onu = onu,
                    action = "reboot_onu",
                    source = "api",
                    details = """{"external_id":"$externalId"}"""
                )
            )
            return SmartOltActionResponseDto(status = true, unique_external_id = externalId)
        } catch (ex: Exception) {
            task.status = "failed"
            task.error = ex.message
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            throw ex
        }
    }

    open fun externalIdBySn(sn: String): String? = findExistingBySn(sn)?.externalId

    private fun releaseSoftDeletedSn(sn: String) {
        val existing = onuRepository.findBySn(sn).orElse(null) ?: return
        if (existing.deletedAt == null) return
        existing.sn = tombstoneSn(existing.sn, existing.id)
        existing.updatedAt = Instant.now()
        onuRepository.saveAndFlush(existing)
    }

    private fun tombstoneSn(sn: String, id: Long?): String {
        val suffix = id?.toString() ?: Instant.now().toEpochMilli().toString()
        val base = sn.takeWhile { it != '#' }.take(48)
        return "$base#del#$suffix"
    }

    private fun requireOlt(): OltMgrOlt {
        return oltRepository.findByName(properties.oltId)
            .orElseThrow { IllegalStateException("OLT seed missing for ${properties.oltId}") }
    }

    private fun findExistingBySn(sn: String): OltMgrOnu? {
        return onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(sn).orElse(null)
    }

    private fun findExistingByPosition(oltId: Long, board: Int, port: Int, onuIndex: Int): OltMgrOnu? {
        return onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(
            oltId,
            board,
            port,
            onuIndex
        ).orElse(null)
    }

    private fun responseFromDb(onu: OltMgrOnu): SmartOltOnuBySnResponseDto {
        val status = onu.status ?: statusRepository.findById(onu.id!!).orElse(null)
        return mapper.toOnuBySnResponseFromDb(onu, status, properties.oltId)
    }

    private fun resolveZone(name: String): OltMgrZone? {
        if (name.isBlank()) return null
        return zoneRepository.findByName(name).orElseGet {
            zoneRepository.save(OltMgrZone(name = name))
        }
    }

    private fun resolveOnuType(name: String): OltMgrOnuType? {
        if (name.isBlank()) return null
        return onuTypeRepository.findByName(name).orElseGet {
            onuTypeRepository.save(OltMgrOnuType(name = name))
        }
    }
}
