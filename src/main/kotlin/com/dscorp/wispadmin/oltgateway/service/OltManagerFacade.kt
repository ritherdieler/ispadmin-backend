package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.MoveOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.api.UpdateWanFormDto
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
import com.dscorp.wispadmin.oltgateway.exception.OltGatewayValidationException
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import com.dscorp.wispadmin.oltgateway.mapper.SmartOltCompatMapper
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

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
        return mapper.toUnconfirmedOnuResponse(queryFacade.autofindParsed(), properties.oltId)
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
            externalId = "${properties.oltId}_${parsed.slot}_${parsed.port}_${parsed.ontId}",
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

    @Transactional
    open fun authorizeOnu(request: AuthorizeOnuFormDto): SmartOltActionResponseDto {
        if (onuRepository.findBySnAndDeletedAtIsNull(request.sn).isPresent) {
            throw OltGatewayConflictException("ONU already authorized for SN=${request.sn}")
        }
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

            val externalId = "${properties.oltId}_${board}_${port}_$nextOntId"
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
                    administrativeStatus = "enabled"
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
            onu.externalId = "${properties.oltId}_${toBoard}_${toPort}_${onu.onuIndex}"
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
            onu.deletedAt = Instant.now()
            onu.updatedAt = Instant.now()
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

    @Transactional
    open fun updateOnuWan(externalId: String, request: UpdateWanFormDto): SmartOltActionResponseDto =
        applyWanUpdate(externalId, request, "set_wan_mode")

    @Transactional
    open fun updateOnuVlan(externalId: String, vlan: String): SmartOltActionResponseDto =
        applyWanUpdate(externalId, UpdateWanFormDto(vlan = vlan), "update_vlan")

    private fun applyWanUpdate(
        externalId: String,
        request: UpdateWanFormDto,
        action: String
    ): SmartOltActionResponseDto {
        val onu = onuRepository.findByExternalIdAndDeletedAtIsNull(externalId)
            .orElseThrow { OnuNotFoundException("ONU not found for externalId=$externalId") }
        val update = parseWanUpdate(request)
        val olt = requireOlt()
        val task = taskRepository.save(
            OltMgrTask(
                olt = olt,
                onu = onu,
                type = action,
                payload = """{"external_id":"$externalId","vlan":${update.vlan}}""",
                status = "running",
                source = "api",
                startedAt = Instant.now()
            )
        )
        try {
            commandService.updateWan(
                UpdateWanCliRequest(
                    board = onu.board,
                    port = onu.port,
                    ontId = onu.onuIndex,
                    vlan = update.vlan,
                    ipAddress = update.ipAddress,
                    subnetMask = update.subnetMask,
                    gateway = update.gateway,
                    dns1 = update.dns1,
                    dns2 = update.dns2
                )
            )
            update.vlan?.let { onu.mainVlanId = it }
            update.ipAddress?.let {
                onu.ipAddress = it
                onu.wanMode = STATIC_WAN_MODE
            }
            update.subnetMask?.let { onu.subnetMask = it }
            update.gateway?.let { onu.defaultGateway = it }
            update.dns1?.let { onu.dns1 = it }
            update.dns2?.let { onu.dns2 = it }
            onu.updatedAt = Instant.now()
            onuRepository.save(onu)
            task.status = "success"
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            auditLogRepository.save(
                OltMgrAuditLog(
                    olt = olt,
                    onu = onu,
                    action = action,
                    source = "api",
                    details = """{"external_id":"$externalId","vlan":${update.vlan}}"""
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

    private fun parseWanUpdate(request: UpdateWanFormDto): WanUpdate {
        val rawVlan = request.vlan.trimToNull()
        val vlan = rawVlan?.let {
            it.toIntOrNull()?.takeIf { value -> value in MIN_VLAN..MAX_VLAN }
                ?: throw OltGatewayValidationException("Invalid vlan '$it', expected $MIN_VLAN-$MAX_VLAN")
        }
        val ipAddress = request.ip_address.trimToNull()
        val subnetMask = request.subnet_mask.trimToNull()
        val gateway = request.default_gateway.trimToNull()
        val dns1 = request.dns1.trimToNull()
        val dns2 = request.dns2.trimToNull()

        if (ipAddress != null && subnetMask == null) {
            throw OltGatewayValidationException("subnet_mask is required when ip_address is provided")
        }
        if (subnetMask != null && ipAddress == null) {
            throw OltGatewayValidationException("ip_address is required when subnet_mask is provided")
        }
        if (ipAddress == null && (gateway != null || dns1 != null || dns2 != null)) {
            throw OltGatewayValidationException("ip_address and subnet_mask are required to set gateway or DNS")
        }
        if (vlan == null && ipAddress == null) {
            throw OltGatewayValidationException("Nothing to update: send vlan or the WAN parameters")
        }
        return WanUpdate(vlan, ipAddress, subnetMask, gateway, dns1, dns2)
    }

    private data class WanUpdate(
        val vlan: Int?,
        val ipAddress: String?,
        val subnetMask: String?,
        val gateway: String?,
        val dns1: String?,
        val dns2: String?
    )

    private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

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

    companion object {
        private const val STATIC_WAN_MODE = "static"
        private const val MIN_VLAN = 1
        private const val MAX_VLAN = 4094
    }
}
