package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuType
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuTypeRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrZoneRepository
import com.dscorp.wispadmin.oltgateway.dto.SmartOltImportResultDto
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltCatalogClient
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltConfiguredOnuDto
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltOnuTypeCatalogDto
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltZoneDto
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

open class SmartOltImportService(
    private val catalogClient: SmartOltCatalogClient,
    private val oltRepository: OltMgrOltRepository,
    private val zoneRepository: OltMgrZoneRepository,
    private val onuTypeRepository: OltMgrOnuTypeRepository,
    private val onuRepository: OltMgrOnuRepository,
    private val statusRepository: OltMgrOnuStatusCurrentRepository,
    private val auditLogRepository: OltMgrAuditLogRepository,
    private val properties: OltGatewayProperties
) {

    private val logger = LoggerFactory.getLogger(SmartOltImportService::class.java)

    @Transactional
    open fun importFromSmartOlt(pageSize: Int = 100, maxPages: Int? = null): SmartOltImportResultDto {
        val startedAt = Instant.now()
        return try {
            val olt = requireOlt()
            val zoneByName = importZones().associateBy { it.name }
            val typeByName = importOnuTypes().associateBy { it.name }
            var inserted = 0
            var updated = 0
            var unchanged = 0
            var pagesFetched = 0
            var totalItems = 0
            var page = 1
            var knownTotalPages = 0
            while (true) {
                if (maxPages != null && pagesFetched >= maxPages) break
                val pageDto = catalogClient.fetchAllOnusDetails(page, pageSize)
                if (page == 1) {
                    totalItems = pageDto.totalItems
                    knownTotalPages = pageDto.totalPages
                }
                if (pageDto.onus.isEmpty()) break
                pagesFetched++
                for (item in pageDto.onus) {
                    when (upsertOnu(olt, item, zoneByName, typeByName)) {
                        UpsertOutcome.INSERTED -> inserted++
                        UpsertOutcome.UPDATED -> updated++
                        UpsertOutcome.UNCHANGED -> unchanged++
                    }
                }
                if (knownTotalPages > 0 && page >= knownTotalPages) break
                page++
            }
            auditLogRepository.save(
                OltMgrAuditLog(
                    olt = olt,
                    action = "smartolt_import",
                    source = "import",
                    details = """{"inserted":$inserted,"updated":$updated,"pages":$pagesFetched}"""
                )
            )
            SmartOltImportResultDto(
                zonesImported = zoneByName.size,
                onuTypesImported = typeByName.size,
                inserted = inserted,
                updated = updated,
                unchanged = unchanged,
                pagesFetched = pagesFetched,
                totalItems = totalItems,
                durationMs = Instant.now().toEpochMilli() - startedAt.toEpochMilli()
            )
        } catch (ex: Exception) {
            logger.warn("SmartOLT import failed: {}", ex.message)
            SmartOltImportResultDto(
                durationMs = Instant.now().toEpochMilli() - startedAt.toEpochMilli(),
                error = ex.message
            )
        }
    }

    private fun importZones(): List<OltMgrZone> {
        val saved = mutableListOf<OltMgrZone>()
        for (dto in catalogClient.fetchZones().response) {
            if (dto.name.isBlank()) continue
            val zone = zoneRepository.findByName(dto.name).orElseGet {
                zoneRepository.save(OltMgrZone(name = dto.name))
            }
            saved += zone
        }
        return saved
    }

    private fun importOnuTypes(): List<OltMgrOnuType> {
        val saved = mutableListOf<OltMgrOnuType>()
        for (dto in catalogClient.fetchOnuTypes().response) {
            if (dto.name.isBlank()) continue
            val existing = onuTypeRepository.findByName(dto.name)
            val type = if (existing.isPresent) {
                applyOnuTypeCatalog(existing.get(), dto)
            } else {
                OltMgrOnuType(
                    name = dto.name,
                    ponType = dto.ponType.ifBlank { "gpon" }.lowercase(Locale.US),
                    capability = normalizeCapability(dto.capability),
                    ethernetPorts = dto.ethernetPorts.toIntOrNull() ?: 1,
                    wifiPorts = dto.wifiPorts.toIntOrNull() ?: 0,
                    voipPorts = dto.voipPorts.toIntOrNull() ?: 0,
                    catvPorts = dto.catv.toIntOrNull() ?: 0,
                    allowCustomProfiles = dto.allowCustomProfiles != "0"
                )
            }
            saved += onuTypeRepository.save(type)
        }
        return saved
    }

    private fun applyOnuTypeCatalog(type: OltMgrOnuType, dto: SmartOltOnuTypeCatalogDto): OltMgrOnuType {
        type.ponType = dto.ponType.ifBlank { type.ponType }.lowercase(Locale.US)
        type.capability = normalizeCapability(dto.capability)
        type.ethernetPorts = dto.ethernetPorts.toIntOrNull() ?: type.ethernetPorts
        type.wifiPorts = dto.wifiPorts.toIntOrNull() ?: type.wifiPorts
        type.voipPorts = dto.voipPorts.toIntOrNull() ?: type.voipPorts
        type.catvPorts = dto.catv.toIntOrNull() ?: type.catvPorts
        type.allowCustomProfiles = dto.allowCustomProfiles != "0"
        return type
    }

    private fun upsertOnu(
        olt: OltMgrOlt,
        item: SmartOltConfiguredOnuDto,
        zoneByName: Map<String, OltMgrZone>,
        typeByName: Map<String, OltMgrOnuType>
    ): UpsertOutcome {
        val sn = HuaweiGponSnmpCodec.normalizeOntSn(item.sn)
        if (sn.isBlank()) return UpsertOutcome.UNCHANGED
        val existing = onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(sn)
            .or { onuRepository.findBySn(sn) }
        if (existing.isPresent) {
            val onu = existing.get()
            if (onu.deletedAt != null) return UpsertOutcome.UNCHANGED
            return if (applySmartOltMetadata(onu, item, zoneByName, typeByName)) {
                onu.updatedAt = Instant.now()
                onuRepository.save(onu)
                UpsertOutcome.UPDATED
            } else {
                UpsertOutcome.UNCHANGED
            }
        }
        val board = item.board.toIntOrNull() ?: return UpsertOutcome.UNCHANGED
        val port = item.port.toIntOrNull() ?: return UpsertOutcome.UNCHANGED
        val onuIndex = item.onu.toIntOrNull() ?: return UpsertOutcome.UNCHANGED
        val externalId = item.uniqueExternalId.ifBlank {
            "${properties.oltId}_${board}_${port}_$onuIndex"
        }
        val onu = OltMgrOnu(
            sn = sn,
            externalId = externalId,
            olt = olt,
            board = board,
            port = port,
            onuIndex = onuIndex,
            importedFromOlt = true,
            syncedAfterImport = parseBooleanFlag(item.isSyncedAfterImport.orEmpty()),
            lastResyncFailed = parseBooleanFlag(item.isFailedResyncConfig.orEmpty())
        )
        applySmartOltMetadata(onu, item, zoneByName, typeByName)
        val saved = onuRepository.save(onu)
        val rx = parseDbm(item.signal1490.orEmpty())
        val status = OltMgrOnuStatusCurrent(
            onu = saved,
            runState = normalizeRunState(item.status.orEmpty()) ?: "offline",
            signalCategory = SIGNAL_CATEGORY_CALCULATOR.fromOnuRxDbm(rx?.toDouble())?.value
                ?: normalizeSignalCategory(item.signal.orEmpty()),
            onuRxDbm = rx,
            oltRxDbm = parseDbm(item.signal1310.orEmpty()),
            polledAt = Instant.now()
        )
        saved.status = status
        statusRepository.save(status)
        return UpsertOutcome.INSERTED
    }

    private fun applySmartOltMetadata(
        onu: OltMgrOnu,
        item: SmartOltConfiguredOnuDto,
        zoneByName: Map<String, OltMgrZone>,
        typeByName: Map<String, OltMgrOnuType>
    ): Boolean {
        var changed = false
        val zoneName = item.zoneName.orEmpty()
        val zone = zoneByName[zoneName].takeIf { zoneName.isNotBlank() }
            ?: zoneName.takeIf { it.isNotBlank() }?.let { zoneRepository.findByName(it).orElse(null) }
        if (zone != null && onu.zone?.id != zone.id) {
            onu.zone = zone
            changed = true
        }
        if (zoneName.isNotBlank() && onu.zoneName != zoneName) {
            onu.zoneName = zoneName
            changed = true
        }
        val onuTypeName = item.onuTypeName.orEmpty()
        val type = typeByName[onuTypeName].takeIf { onuTypeName.isNotBlank() }
            ?: onuTypeName.takeIf { it.isNotBlank() }?.let { onuTypeRepository.findByName(it).orElse(null) }
        if (type != null && onu.onuType?.id != type.id) {
            onu.onuType = type
            changed = true
        }
        if (onuTypeName.isNotBlank() && onu.onuTypeName != onuTypeName) {
            onu.onuTypeName = onuTypeName
            changed = true
        }
        changed = setIfChanged(onu::ponType, item.ponType.lowercase(Locale.US).ifBlank { "gpon" }) || changed
        item.name?.takeIf { it.isNotBlank() }?.let { name ->
            if (onu.name != name) {
                onu.name = name
                changed = true
            }
        }
        item.address?.takeIf { it.isNotBlank() }?.let { address ->
            if (onu.address != address) {
                onu.address = address
                changed = true
            }
        }
        item.contact?.takeIf { it.isNotBlank() }?.let { contact ->
            if (onu.contact != contact) {
                onu.contact = contact
                changed = true
            }
        }
        val mode = normalizeMode(item.mode.orEmpty())
        if (mode != null && onu.mode != mode) {
            onu.mode = mode
            changed = true
        }
        val wanMode = normalizeWanMode(item.wanMode.orEmpty())
        if (wanMode != null && onu.wanMode != wanMode) {
            onu.wanMode = wanMode
            changed = true
        }
        item.customTemplateName?.takeIf { it.isNotBlank() }?.let { profile ->
            if (onu.customProfile != profile) {
                onu.customProfile = profile
                changed = true
            }
        }
        val adminStatus = normalizeAdminStatus(item.administrativeStatus.orEmpty())
        if (onu.administrativeStatus != adminStatus) {
            onu.administrativeStatus = adminStatus
            changed = true
        }
        val vlan = item.vlan.orEmpty().toIntOrNull()
        if (vlan != null && onu.mainVlanId != vlan) {
            onu.mainVlanId = vlan
            changed = true
        }
        val authDate = parseAuthorizationDate(item.authorizationDate.orEmpty())
        if (authDate != null && onu.authorizationDate != authDate) {
            onu.authorizationDate = authDate
            changed = true
        }
        val synced = parseBooleanFlag(item.isSyncedAfterImport.orEmpty())
        if (onu.syncedAfterImport != synced) {
            onu.syncedAfterImport = synced
            changed = true
        }
        val resyncFailed = parseBooleanFlag(item.isFailedResyncConfig.orEmpty())
        if (onu.lastResyncFailed != resyncFailed) {
            onu.lastResyncFailed = resyncFailed
            changed = true
        }
        item.odbName?.takeIf { it.isNotBlank() }?.let { odbName ->
            val splitterId = stableSplitterId(odbName)
            if (onu.splitterId != splitterId) {
                onu.splitterId = splitterId
                changed = true
            }
        }
        val odbPort = item.odbPort.orEmpty().toIntOrNull()
        if (odbPort != null && onu.splitterPort != odbPort) {
            onu.splitterPort = odbPort
            changed = true
        }
        onu.importedFromOlt = true
        changed = applyStatusMetadata(onu, item) || changed
        return changed
    }

    private fun applyStatusMetadata(onu: OltMgrOnu, item: SmartOltConfiguredOnuDto): Boolean {
        val runState = normalizeRunState(item.status.orEmpty())
        val rx = parseDbm(item.signal1490.orEmpty())
        val oltRx = parseDbm(item.signal1310.orEmpty())
        val signalCategory = SIGNAL_CATEGORY_CALCULATOR.fromOnuRxDbm(rx?.toDouble())?.value
            ?: normalizeSignalCategory(item.signal.orEmpty())
        val status = onu.status
        if (status == null) {
            val created = OltMgrOnuStatusCurrent(
                onu = onu,
                runState = normalizeRunState(item.status.orEmpty()) ?: "offline",
                signalCategory = signalCategory,
                onuRxDbm = rx,
                oltRxDbm = oltRx,
                polledAt = Instant.now()
            )
            onu.status = created
            statusRepository.save(created)
            return true
        }
        var changed = false
        if (runState != null && status.runState != runState) {
            status.runState = runState
            changed = true
        }
        if (signalCategory != null && status.signalCategory != signalCategory) {
            status.signalCategory = signalCategory
            changed = true
        }
        if (rx != null && status.onuRxDbm != rx) {
            status.onuRxDbm = rx
            changed = true
        }
        if (oltRx != null && status.oltRxDbm != oltRx) {
            status.oltRxDbm = oltRx
            changed = true
        }
        if (changed) {
            status.polledAt = Instant.now()
            statusRepository.save(status)
        }
        return changed
    }

    private fun stableSplitterId(odbName: String): Long =
        odbName.uppercase(Locale.US).hashCode().toLong().let { if (it < 0) -it else it }

    private fun setIfChanged(property: kotlin.reflect.KMutableProperty0<String>, value: String): Boolean {
        if (property.get() == value) return false
        property.set(value)
        return true
    }

    private fun requireOlt(): OltMgrOlt =
        oltRepository.findByName(properties.oltId)
            .orElseThrow { IllegalStateException("OLT seed missing for ${properties.oltId}") }

    private fun normalizeCapability(value: String): String =
        value.lowercase(Locale.US).replace("/", "_").replace(" ", "_").ifBlank { "bridging_routing" }

    private fun normalizeMode(value: String): String? =
        value.trim().lowercase(Locale.US).takeIf { it.isNotBlank() }

    private fun normalizeWanMode(value: String): String? = when (value.trim().lowercase(Locale.US)) {
        "setup via onu webpage" -> "onu_webpage"
        "dhcp" -> "dhcp"
        "static", "static ip (from ip pools)" -> "static"
        "pppoe" -> "pppoe"
        else -> value.trim().lowercase(Locale.US).takeIf { it.isNotBlank() }
    }

    private fun normalizeAdminStatus(value: String): String =
        value.trim().lowercase(Locale.US).ifBlank { "enabled" }

    private fun normalizeRunState(value: String): String? = when (value.trim().lowercase(Locale.US)) {
        "online" -> "online"
        "offline" -> "offline"
        "power fail", "pwrfail", "power_fail" -> "offline"
        "loss of signal", "los" -> "offline"
        "admin disabled", "disabled" -> "offline"
        "" -> null
        else -> value.trim().lowercase(Locale.US).replace(" ", "_")
    }

    private fun normalizeSignalCategory(value: String): String? = when (value.trim().lowercase(Locale.US)) {
        "good" -> "good"
        "warning" -> "warning"
        "critical" -> "critical"
        "" -> null
        else -> value.trim().lowercase(Locale.US)
    }

    private fun parseBooleanFlag(value: String): Boolean =
        value == "1" || value.equals("true", ignoreCase = true)

    private fun parseAuthorizationDate(value: String): Instant? {
        if (value.isBlank()) return null
        return try {
            LocalDateTime.parse(value.trim(), AUTH_DATE_FORMAT).toInstant(ZoneOffset.UTC)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDbm(value: String): BigDecimal? {
        val token = value.substringBefore(" ").trim()
        return token.toBigDecimalOrNull()
    }

    private enum class UpsertOutcome {
        INSERTED,
        UPDATED,
        UNCHANGED
    }

    companion object {
        private val AUTH_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val SIGNAL_CATEGORY_CALCULATOR = SignalCategoryCalculator()
    }
}
