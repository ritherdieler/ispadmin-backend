package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSyncRun
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuTypeRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrSyncRunRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrZoneRepository
import com.dscorp.wispadmin.oltgateway.dto.BoardPortCatalogDto
import com.dscorp.wispadmin.oltgateway.dto.CatalogItemDto
import com.dscorp.wispadmin.oltgateway.dto.CatalogStringItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuDetailDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuFilter
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuHistoryDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuHistoryItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuLiveStatusDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuPageDto
import com.dscorp.wispadmin.oltgateway.dto.OnuCatalogsDto
import com.dscorp.wispadmin.oltgateway.dto.SyncResultDto
import com.dscorp.wispadmin.oltgateway.dto.SyncStatusDto
import com.dscorp.wispadmin.oltgateway.exception.CliBusBusyException
import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpClient
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

open class OltInventorySyncService(
    queryFacade: OltGatewayQueryFacade,
    oltRepository: OltMgrOltRepository,
    onuRepository: OltMgrOnuRepository,
    statusRepository: OltMgrOnuStatusCurrentRepository,
    auditLogRepository: OltMgrAuditLogRepository,
    syncRunRepository: OltMgrSyncRunRepository,
    taskRepository: OltMgrTaskRepository,
    properties: OltGatewayProperties,
    cliBus: OltCliBus? = null,
    transactionTemplate: TransactionTemplate? = null,
    snmpClient: OltSnmpClient? = null,
    zoneRepository: OltMgrZoneRepository? = null,
    onuTypeRepository: OltMgrOnuTypeRepository? = null,
    eventPublisher: org.springframework.context.ApplicationEventPublisher? = null
) {

    companion object {
        private val telemetryPublisher = AtomicReference<org.springframework.context.ApplicationEventPublisher?>(null)
        private val logger = LoggerFactory.getLogger(OltInventorySyncService::class.java)
        private val SIGNAL_CATEGORY_CALCULATOR = SignalCategoryCalculator()
        private const val NAME_MAX = 512
        /** Soft-deleted rows keep unique (olt,board,port,onu_index); park them here. */
        private const val TOMBSTONE_BOARD = -1
        /** Temporary board while swapping positions in the same sync flush. */
        private const val STAGING_BOARD = -2
        private val running = AtomicBoolean(false)
        private val lastStartedAtRef = AtomicReference<Instant?>(null)
        private val lastResultRef = AtomicReference<SyncResult?>(null)
        private val propertiesRef = AtomicReference<OltGatewayProperties?>(null)
        private val queryFacadeRef = AtomicReference<OltGatewayQueryFacade?>(null)
        private val oltRepositoryRef = AtomicReference<OltMgrOltRepository?>(null)
        private val onuRepositoryRef = AtomicReference<OltMgrOnuRepository?>(null)
        private val statusRepositoryRef = AtomicReference<OltMgrOnuStatusCurrentRepository?>(null)
        private val auditLogRepositoryRef = AtomicReference<OltMgrAuditLogRepository?>(null)
        private val syncRunRepositoryRef = AtomicReference<OltMgrSyncRunRepository?>(null)
        private val taskRepositoryRef = AtomicReference<OltMgrTaskRepository?>(null)
        private val cliBusRef = AtomicReference<OltCliBus?>(null)
        private val transactionTemplateRef = AtomicReference<TransactionTemplate?>(null)
        private val snmpClientRef = AtomicReference<OltSnmpClient?>(null)
        private val zoneRepositoryRef = AtomicReference<OltMgrZoneRepository?>(null)
        private val onuTypeRepositoryRef = AtomicReference<OltMgrOnuTypeRepository?>(null)
    }

    init {
        telemetryPublisher.set(eventPublisher)
        propertiesRef.set(properties)
        queryFacadeRef.set(queryFacade)
        oltRepositoryRef.set(oltRepository)
        onuRepositoryRef.set(onuRepository)
        statusRepositoryRef.set(statusRepository)
        auditLogRepositoryRef.set(auditLogRepository)
        syncRunRepositoryRef.set(syncRunRepository)
        taskRepositoryRef.set(taskRepository)
        cliBusRef.set(cliBus)
        transactionTemplateRef.set(transactionTemplate)
        zoneRepositoryRef.set(zoneRepository)
        onuTypeRepositoryRef.set(onuTypeRepository)
        snmpClientRef.set(snmpClient)
    }

    private fun props(): OltGatewayProperties {
        return propertiesRef.get()
            ?: error("OltGatewayProperties unavailable")
    }

    private fun queryFacade(): OltGatewayQueryFacade =
        queryFacadeRef.get() ?: error("OltGatewayQueryFacade unavailable")

    private fun oltRepository(): OltMgrOltRepository =
        oltRepositoryRef.get() ?: error("OltMgrOltRepository unavailable")

    private fun onuRepository(): OltMgrOnuRepository =
        onuRepositoryRef.get() ?: error("OltMgrOnuRepository unavailable")

    private fun statusRepository(): OltMgrOnuStatusCurrentRepository =
        statusRepositoryRef.get() ?: error("OltMgrOnuStatusCurrentRepository unavailable")

    private fun auditLogRepository(): OltMgrAuditLogRepository =
        auditLogRepositoryRef.get() ?: error("OltMgrAuditLogRepository unavailable")

    private fun syncRunRepository(): OltMgrSyncRunRepository =
        syncRunRepositoryRef.get() ?: error("OltMgrSyncRunRepository unavailable")

    private fun taskRepository(): OltMgrTaskRepository =
        taskRepositoryRef.get() ?: error("OltMgrTaskRepository unavailable")

    private fun cliBus(): OltCliBus? = cliBusRef.get()

    private fun snmpClient(): OltSnmpClient? = snmpClientRef.get()

    private fun zoneRepository(): OltMgrZoneRepository? = zoneRepositoryRef.get()

    private fun onuTypeRepository(): OltMgrOnuTypeRepository? = onuTypeRepositoryRef.get()

    fun isRunning(): Boolean = running.get()

    fun lastStartedAt(): Instant? = lastStartedAtRef.get()

    fun lastResult(): SyncResult? = lastResultRef.get()

    fun status(): SyncStatusDto {
        return SyncStatusDto(
            running = running.get(),
            lastStartedAt = lastStartedAtRef.get()?.toString(),
            lastResult = lastResultRef.get()?.toDto(),
            busQueueDepth = cliBus()?.queueDepth() ?: 0,
            busBusyJobType = cliBus()?.busyJobType()?.name
        )
    }

    @Transactional(readOnly = true)
    open fun listConfigured(
        page: Int,
        size: Int,
        filter: ConfiguredOnuFilter = ConfiguredOnuFilter()
    ): ConfiguredOnuPageDto {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 200),
            Sort.by(Sort.Order.desc("authorizationDate").nullsLast())
        )
        val resolved = resolveFilter(filter)
        val result = onuRepository().findConfiguredFiltered(
            q = resolved.q,
            board = resolved.board,
            port = resolved.port,
            oltId = resolved.oltId,
            zoneId = resolved.zoneId,
            vlan = resolved.vlan,
            onuTypeId = resolved.onuTypeId,
            onuTypeName = resolved.onuTypeName,
            customProfile = resolved.customProfile,
            ponType = resolved.ponType,
            mode = resolved.mode,
            runState = resolved.runState,
            signalCategory = resolved.signalCategory,
            splitterId = resolved.splitterId,
            configurationMethod = resolved.configurationMethod,
            wanMode = resolved.wanMode,
            mgmtIpMode = resolved.mgmtIpMode,
            importedSynced = resolved.importedSynced,
            lastResyncFailed = resolved.lastResyncFailed,
            lineProfileMaptype = resolved.lineProfileMaptype,
            administrativeStatus = resolved.administrativeStatus,
            lastDownCause = resolved.lastDownCause,
            pageable = pageable
        )
        val items = result.content.map { onu -> toConfiguredItem(onu) }
        return ConfiguredOnuPageDto(
            items = items,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional(readOnly = true)
    open fun getConfiguredByExternalId(externalId: String): ConfiguredOnuDetailDto? {
        val key = externalId.trim()
        if (key.isEmpty()) return null
        val onu = onuRepository().findByExternalIdAndDeletedAtIsNull(key).orElse(null) ?: return null
        return toConfiguredDetail(onu)
    }

    @Transactional
    open fun getLiveStatusByExternalId(externalId: String): ConfiguredOnuLiveStatusDto? {
        val key = externalId.trim()
        if (key.isEmpty()) return null
        val onu = onuRepository().findByExternalIdAndDeletedAtIsNull(key).orElse(null) ?: return null
        val detail = queryFacade().onuDetail(onu.board, onu.port, onu.onuIndex)
        val optical = queryFacade().optical(onu.board, onu.port, onu.onuIndex)
        val matchState = optical.matchState ?: onu.status?.matchState
        val distanceM = optical.distanceM ?: onu.status?.distanceM
        val temperatureC = optical.temperatureC
        persistLiveOpticalSnapshot(
            onu = onu,
            temperatureC = temperatureC,
            distanceM = optical.distanceM,
            matchState = optical.matchState,
            onuRxDbm = optical.rxPowerDbm,
            onuTxDbm = optical.txPowerDbm,
            oltRxDbm = optical.oltRxPowerDbm
        )
        return ConfiguredOnuLiveStatusDto(
            sn = detail.sn.ifBlank { onu.sn },
            runState = detail.runState,
            matchState = matchState,
            controlFlag = detail.controlFlag,
            description = detail.description,
            onuRxDbm = optical.rxPowerDbm,
            onuTxDbm = optical.txPowerDbm,
            oltRxDbm = optical.oltRxPowerDbm,
            temperatureC = temperatureC,
            voltageV = optical.voltageV,
            biasCurrentMa = optical.biasCurrentMa,
            distanceM = distanceM,
            lineProfileName = detail.lineProfileName ?: onu.lineProfileName,
            serviceProfileName = detail.serviceProfileName ?: onu.serviceProfileName
        )
    }

    private fun persistLiveOpticalSnapshot(
        onu: OltMgrOnu,
        temperatureC: Double?,
        distanceM: Int?,
        matchState: String?,
        onuRxDbm: Double?,
        onuTxDbm: Double?,
        oltRxDbm: Double?
    ) {
        val status = onu.status ?: return
        var changed = false
        if (temperatureC != null) {
            val nextTemp = temperatureC.toInt()
            if (status.temperatureC != nextTemp) {
                status.temperatureC = nextTemp
                changed = true
            }
        }
        if (distanceM != null && status.distanceM != distanceM) {
            status.distanceM = distanceM
            changed = true
        }
        if (matchState != null && status.matchState != matchState) {
            status.matchState = matchState
            changed = true
        }
        if (onuRxDbm != null) {
            val next = java.math.BigDecimal.valueOf(onuRxDbm).setScale(2, java.math.RoundingMode.HALF_UP)
            if (status.onuRxDbm == null || status.onuRxDbm!!.compareTo(next) != 0) {
                status.onuRxDbm = next
                changed = true
            }
        }
        if (onuTxDbm != null) {
            val next = java.math.BigDecimal.valueOf(onuTxDbm).setScale(2, java.math.RoundingMode.HALF_UP)
            if (status.onuTxDbm == null || status.onuTxDbm!!.compareTo(next) != 0) {
                status.onuTxDbm = next
                changed = true
            }
        }
        if (oltRxDbm != null) {
            val next = java.math.BigDecimal.valueOf(oltRxDbm).setScale(2, java.math.RoundingMode.HALF_UP)
            if (status.oltRxDbm == null || status.oltRxDbm!!.compareTo(next) != 0) {
                status.oltRxDbm = next
                changed = true
            }
        }
        if (!changed) return
        status.polledAt = Instant.now()
        statusRepository().save(status)
    }

    @Transactional(readOnly = true)
    open fun getHistoryByExternalId(externalId: String, limit: Int = 50): ConfiguredOnuHistoryDto? {
        val key = externalId.trim()
        if (key.isEmpty()) return null
        val onu = onuRepository().findByExternalIdAndDeletedAtIsNull(key).orElse(null) ?: return null
        val size = limit.coerceIn(1, 200)
        val logs = auditLogRepository().findByOnu_IdOrderByCreatedAtDesc(
            onu.id!!,
            PageRequest.of(0, size)
        )
        return ConfiguredOnuHistoryDto(
            items = logs.map { log ->
                ConfiguredOnuHistoryItemDto(
                    id = log.id!!,
                    action = log.action,
                    userId = log.userId,
                    ipAddress = log.ipAddress,
                    details = log.details,
                    createdAt = log.createdAt.toString()
                )
            }
        )
    }

    @Transactional(readOnly = true)
    open fun listCatalogs(): OnuCatalogsDto {
        val olts = oltRepository().findAll().mapNotNull { olt ->
            val id = olt.id ?: return@mapNotNull null
            CatalogItemDto(id = id, name = olt.name)
        }
        val zones = zoneRepository()?.findAll()?.mapNotNull { z ->
            val id = z.id ?: return@mapNotNull null
            CatalogItemDto(id = id, name = z.name)
        } ?: emptyList()
        val types = onuTypeRepository()?.findAll()?.mapNotNull { t ->
            val id = t.id ?: return@mapNotNull null
            CatalogItemDto(id = id, name = t.name)
        } ?: emptyList()
        val splitters = onuRepository().findDistinctSplitterIds().map { id ->
            CatalogItemDto(id = id, name = "Splitter $id")
        }
        return OnuCatalogsDto(
            olts = olts,
            zones = zones,
            onuTypes = types,
            vlans = onuRepository().findDistinctVlans(),
            profiles = onuRepository().findDistinctProfiles().map { CatalogStringItemDto(it) },
            splitters = splitters,
            ponTypes = onuRepository().findDistinctPonTypes().map { CatalogStringItemDto(it) }
        )
    }

    @Transactional(readOnly = true)
    open fun listBoardsPorts(oltId: Long? = null, board: Int? = null): BoardPortCatalogDto {
        return BoardPortCatalogDto(
            boards = onuRepository().findDistinctBoards(oltId),
            ports = onuRepository().findDistinctPorts(oltId, board)
        )
    }

    private fun resolveFilter(filter: ConfiguredOnuFilter): ConfiguredOnuFilter {
        val q = filter.q?.trim()?.takeIf { it.isNotEmpty() }
        var runState = filter.runState?.trim()?.takeIf { it.isNotEmpty() }
        var administrativeStatus = filter.administrativeStatus?.trim()?.takeIf { it.isNotEmpty() }
        var lastDownCause = filter.lastDownCause?.trim()?.takeIf { it.isNotEmpty() }
        when (filter.status?.trim()?.lowercase()) {
            "online" -> runState = "online"
            "offline" -> runState = "offline"
            "disabled" -> administrativeStatus = "disabled"
            "pwrfail" -> lastDownCause = "pwr"
            "los" -> lastDownCause = "los"
        }
        val wanMode = when (filter.wanMode?.trim()?.lowercase()) {
            "setup via onu webpage", "onu_webpage" -> "onu_webpage"
            "dhcp" -> "dhcp"
            "static", "static ip (from ip pools)" -> "static"
            "pppoe" -> "pppoe"
            else -> filter.wanMode?.trim()?.takeIf { it.isNotEmpty() }
        }
        return filter.copy(
            q = q,
            onuTypeName = filter.onuTypeName?.trim()?.takeIf { it.isNotEmpty() },
            customProfile = filter.customProfile?.trim()?.takeIf { it.isNotEmpty() },
            ponType = filter.ponType?.trim()?.takeIf { it.isNotEmpty() },
            mode = filter.mode?.trim()?.takeIf { it.isNotEmpty() },
            runState = runState,
            signalCategory = filter.signalCategory?.trim()?.takeIf { it.isNotEmpty() },
            configurationMethod = filter.configurationMethod?.trim()?.takeIf { it.isNotEmpty() },
            wanMode = wanMode,
            mgmtIpMode = filter.mgmtIpMode?.trim()?.takeIf { it.isNotEmpty() },
            lineProfileMaptype = filter.lineProfileMaptype?.trim()?.takeIf { it.isNotEmpty() },
            administrativeStatus = administrativeStatus,
            lastDownCause = lastDownCause
        )
    }

    private fun toConfiguredItem(onu: OltMgrOnu): ConfiguredOnuItemDto {
        val status = onu.status
        val type = onu.onuType
        return ConfiguredOnuItemDto(
            id = onu.id!!,
            sn = onu.sn,
            externalId = onu.externalId,
            board = onu.board,
            port = onu.port,
            onuIndex = onu.onuIndex,
            name = onu.name,
            importedFromOlt = onu.importedFromOlt,
            runState = status?.runState,
            matchState = status?.matchState,
            polledAt = status?.polledAt?.toString(),
            onuRxDbm = status?.onuRxDbm?.toDouble(),
            onuTxDbm = status?.onuTxDbm?.toDouble(),
            oltRxDbm = status?.oltRxDbm?.toDouble(),
            signalCategory = resolveSignalCategory(status),
            oltId = onu.olt.id,
            oltName = onu.olt.name,
            zoneName = onu.zone?.name ?: onu.zoneName,
            splitterId = onu.splitterId,
            mode = onu.mode,
            vlan = onu.mainVlanId,
            onuTypeName = type?.name ?: onu.onuTypeName,
            authorizationDate = onu.authorizationDate?.toString(),
            administrativeStatus = onu.administrativeStatus,
            lastDownCause = status?.lastDownCause,
            hasVoip = (type?.voipPorts ?: 0) > 0,
            hasTv = (type?.catvPorts ?: 0) > 0,
            ponType = onu.ponType,
            customProfile = onu.customProfile,
            syncedAfterImport = onu.syncedAfterImport,
            lastResyncFailed = onu.lastResyncFailed,
            configurationMethod = onu.configurationMethod,
            wanMode = onu.wanMode,
            mgmtIpMode = onu.mgmtIpMode,
            ipAddress = onu.ipAddress,
            address = onu.address,
            contact = onu.contact
        )
    }

    private fun toConfiguredDetail(onu: OltMgrOnu): ConfiguredOnuDetailDto {
        val status = onu.status
        val type = onu.onuType
        return ConfiguredOnuDetailDto(
            id = onu.id!!,
            sn = onu.sn,
            externalId = onu.externalId,
            board = onu.board,
            port = onu.port,
            onuIndex = onu.onuIndex,
            name = onu.name,
            importedFromOlt = onu.importedFromOlt,
            runState = status?.runState,
            matchState = status?.matchState,
            polledAt = status?.polledAt?.toString(),
            onuRxDbm = status?.onuRxDbm?.toDouble(),
            onuTxDbm = status?.onuTxDbm?.toDouble(),
            oltRxDbm = status?.oltRxDbm?.toDouble(),
            signalCategory = resolveSignalCategory(status),
            oltId = onu.olt.id,
            oltName = onu.olt.name,
            zoneName = onu.zone?.name ?: onu.zoneName,
            splitterId = onu.splitterId,
            splitterPort = onu.splitterPort,
            mode = onu.mode,
            vlan = onu.mainVlanId,
            onuTypeName = type?.name ?: onu.onuTypeName,
            authorizationDate = onu.authorizationDate?.toString(),
            administrativeStatus = onu.administrativeStatus,
            lastDownCause = status?.lastDownCause,
            hasVoip = (type?.voipPorts ?: 0) > 0,
            hasTv = (type?.catvPorts ?: 0) > 0,
            ponType = onu.ponType,
            gponChannel = onu.gponChannel,
            customProfile = onu.customProfile,
            syncedAfterImport = onu.syncedAfterImport,
            lastResyncFailed = onu.lastResyncFailed,
            configurationMethod = onu.configurationMethod,
            wanMode = onu.wanMode,
            mgmtIpMode = onu.mgmtIpMode,
            mgmtVlanId = onu.mgmtVlanId,
            mgmtIpAddress = onu.mgmtIpAddress,
            tr069Profile = null,
            ipAddress = onu.ipAddress,
            subnetMask = onu.subnetMask,
            defaultGateway = onu.defaultGateway,
            dns1 = onu.dns1,
            dns2 = onu.dns2,
            address = onu.address,
            contact = onu.contact,
            latitude = onu.latitude?.toDouble(),
            longitude = onu.longitude?.toDouble(),
            lineProfileName = onu.lineProfileName,
            serviceProfileName = onu.serviceProfileName,
            lastStatusChange = status?.lastStatusChange?.toString(),
            temperatureC = status?.temperatureC?.toDouble(),
            distanceM = status?.distanceM,
            ethernetPortCount = type?.ethernetPorts ?: 0,
            wifiPortCount = type?.wifiPorts ?: 0,
            servicePorts = emptyList()
        )
    }

    private fun resolveSignalCategory(status: OltMgrOnuStatusCurrent?): String? {
        val fromRx = SIGNAL_CATEGORY_CALCULATOR.fromOnuRxDbm(status?.onuRxDbm?.toDouble())?.value
        if (fromRx != null) return fromRx
        return status?.signalCategory
    }

    open fun syncInventory(): SyncResult {
        return syncInventoryInternal(source = resolveInventorySource())
    }

    open fun syncInventoryFromSnmp(): SyncResult {
        return syncInventoryInternal(source = InventorySource.SNMP)
    }

    private enum class InventorySource { SSH, SNMP }

    /**
     * Inventory sync is SNMP-first. SSH is deprecated for this task and only used when
     * [OltGatewayProperties.SnmpProperties.allowSshInventoryFallback] is true.
     */
    private fun resolveInventorySource(): InventorySource {
        val properties = props()
        val snmpReady = properties.snmp.enabled &&
            properties.snmp.roCommunity.isNotBlank() &&
            snmpClient() != null
        if (snmpReady) {
            return InventorySource.SNMP
        }
        if (properties.snmp.allowSshInventoryFallback) {
            logger.warn(
                "Inventory sync using deprecated SSH path " +
                    "(enable olt.gateway.snmp + OLT_GATEWAY_SNMP_RO_COMMUNITY to use SNMP)"
            )
            return InventorySource.SSH
        }
        return InventorySource.SNMP
    }

    private fun syncInventoryInternal(source: InventorySource): SyncResult {
        if (!running.compareAndSet(false, true)) {
            return SyncResult(skippedReason = "sync_already_running").also { lastResultRef.set(it) }
        }
        val startedAt = Instant.now()
        lastStartedAtRef.set(startedAt)
        try {
            val properties = props()
            if (properties.sync.skipWhenWriteRunning && taskRepository().existsByStatus("running")) {
                return finish(
                    SyncResult(skippedReason = "write_task_running"),
                    startedAt
                )
            }
            val olt = oltRepository().findByName(properties.oltId)
                .orElseThrow { IllegalStateException("OLT seed missing for ${properties.oltId}") }
            val snapshot = when (source) {
                InventorySource.SSH -> {
                    @Suppress("DEPRECATION")
                    queryFacade().listOnusParsed()
                }
                InventorySource.SNMP -> {
                    val client = snmpClient()
                        ?: return finish(SyncResult(skippedReason = "snmp_client_unavailable"), startedAt)
                    if (!properties.snmp.enabled || properties.snmp.roCommunity.isBlank()) {
                        return finish(SyncResult(skippedReason = "snmp_required"), startedAt)
                    }
                    logger.info("Inventory sync via SNMP GETBULK (SSH inventory deprecated)")
                    client.listConfiguredOnus()
                }
            }
            if (snapshot.isEmpty()) {
                return finish(
                    SyncResult(skippedReason = "empty_snapshot"),
                    startedAt
                )
            }
            val result = transactionTemplateRef.get()?.execute { persistSnapshot(olt, snapshot) }
                ?: persistSnapshot(olt, snapshot)
            return finish(result, startedAt)
        } catch (ex: CliBusBusyException) {
            logger.info("Inventory sync skipped by CLI bus: {}", ex.reason)
            return finish(
                SyncResult(skippedReason = ex.reason),
                startedAt
            )
        } catch (ex: OltUnreachableException) {
            logger.info("Inventory sync skipped: olt unreachable")
            return finish(
                SyncResult(skippedReason = "olt_unreachable"),
                startedAt
            )
        } catch (ex: Exception) {
            logger.warn("Inventory sync failed (source={}): {}", source, ex.message)
            return finish(
                SyncResult(error = ex.message),
                startedAt
            )
        } finally {
            running.set(false)
        }
    }

    open fun persistSnapshot(olt: OltMgrOlt, snapshot: List<ParsedOnuSummary>): SyncResult {
        val now = Instant.now()
        val bySn = snapshot.associateBy { HuaweiGponSnmpCodec.normalizeOntSn(it.sn) }
        val existing = onuRepository().findByOlt_IdWithStatus(olt.id!!)
        val existingBySn = linkedMapOf<String, OltMgrOnu>()
        for (onu in existing) {
            if (onu.deletedAt != null) continue
            existingBySn[HuaweiGponSnmpCodec.normalizeOntSn(onu.sn)] = onu
        }
        for (onu in existing) {
            if (onu.deletedAt == null) continue
            existingBySn.putIfAbsent(HuaweiGponSnmpCodec.normalizeOntSn(onu.sn), onu)
        }
        val pending = PendingWrites()
        val softDeletedOnus = linkedSetOf<OltMgrOnu>()
        val positionMovers = mutableListOf<PositionMove>()

        // Soft-deleted rows still hold unique (olt,board,port,onu_index). Free them first.
        val staleDeleted = existing.filter { it.deletedAt != null && it.board >= 0 }
        if (staleDeleted.isNotEmpty()) {
            for (onu in staleDeleted) {
                tombstoneDeletedOnu(onu, onu.deletedAt ?: now)
            }
            onuRepository().saveAll(staleDeleted)
            onuRepository().flush()
        }

        var inserted = 0
        var updated = 0
        var unchanged = 0
        var softDeleted = 0

        for (parsed in snapshot) {
            val normSn = HuaweiGponSnmpCodec.normalizeOntSn(parsed.sn)
            val current = existingBySn[normSn]
            if (current == null) {
                pending.onus += buildNewOnu(olt, parsed, now)
                inserted++
            } else {
                val beforeBoard = current.board
                val beforePort = current.port
                val beforeIndex = current.onuIndex
                when (applyUpdate(olt, current, parsed, now, pending)) {
                    UpdateOutcome.UPDATED -> {
                        updated++
                        if (current.board != beforeBoard ||
                            current.port != beforePort ||
                            current.onuIndex != beforeIndex
                        ) {
                            positionMovers += PositionMove(
                                onu = current,
                                board = current.board,
                                port = current.port,
                                onuIndex = current.onuIndex
                            )
                        }
                    }
                    UpdateOutcome.UNCHANGED -> unchanged++
                }
            }
        }

        for (onu in existing) {
            if (onu.deletedAt != null) continue
            if (!bySn.containsKey(HuaweiGponSnmpCodec.normalizeOntSn(onu.sn))) {
                tombstoneDeletedOnu(onu, now)
                softDeletedOnus += onu
                pending.audits += OltMgrAuditLog(
                    olt = olt,
                    onu = onu,
                    action = "sync_missing_on_olt",
                    source = "sync",
                    details = """{"sn":"${onu.sn}"}"""
                )
                softDeleted++
            }
        }

        // Unique (olt,board,port,onu_index) ignores deleted_at — free slots before insert/move.
        // Must flush so MySQL sees tombstones/staging before the final batch.
        if (softDeletedOnus.isNotEmpty()) {
            onuRepository().saveAll(softDeletedOnus)
            onuRepository().flush()
        }
        if (positionMovers.isNotEmpty()) {
            for (move in positionMovers) {
                stagePosition(move.onu)
            }
            onuRepository().saveAll(positionMovers.map { it.onu })
            onuRepository().flush()
            for (move in positionMovers) {
                move.onu.board = move.board
                move.onu.port = move.port
                move.onu.onuIndex = move.onuIndex
                move.onu.externalId = externalId(move.board, move.port, move.onuIndex)
            }
        }

        // Avoid double-saving soft-deleted rows in the final batch.
        pending.onus.removeAll(softDeletedOnus)
        flushPending(pending)
        onuRepository().flush()
        logger.info(
            "Inventory sync persisted onus={} statuses={} audits={} softDeleted={} movers={}",
            pending.onus.size,
            pending.statuses.size,
            pending.audits.size,
            softDeletedOnus.size,
            positionMovers.size
        )
        return SyncResult(
            inserted = inserted,
            updated = updated,
            softDeleted = softDeleted,
            unchanged = unchanged
        )
    }

    /** Moves a soft-deleted ONU off its F/S/P so the unique key can be reused. */
    private fun tombstoneDeletedOnu(onu: OltMgrOnu, now: Instant) {
        onu.deletedAt = now
        onu.updatedAt = now
        onu.board = TOMBSTONE_BOARD
        onu.port = 0
        onu.onuIndex = (onu.id ?: 0L).toInt().coerceAtLeast(0)
        onu.externalId = "${props().oltId}_deleted_${onu.id}"
    }

    /** Temporary unique F/S/P while swapping / moving before final positions are flushed. */
    private fun stagePosition(onu: OltMgrOnu) {
        onu.board = STAGING_BOARD
        onu.port = 0
        onu.onuIndex = (onu.id ?: 0L).toInt().coerceAtLeast(0)
        onu.externalId = "${props().oltId}_staging_${onu.id}"
    }

    private fun flushPending(pending: PendingWrites) {
        if (pending.onus.isNotEmpty()) {
            onuRepository().saveAll(pending.onus)
        }
        if (pending.statuses.isNotEmpty()) {
            statusRepository().saveAll(pending.statuses)
        }
        if (pending.audits.isNotEmpty()) {
            auditLogRepository().saveAll(pending.audits)
        }
    }

    private fun buildNewOnu(olt: OltMgrOlt, parsed: ParsedOnuSummary, now: Instant): OltMgrOnu {
        telemetryPublisher.get()?.publishEvent(OltStateObservation(parsed.sn, parsed.runState, parsed.lastDownCause, now))
        val onu = OltMgrOnu(
            sn = parsed.sn,
            externalId = externalId(parsed.slot, parsed.port, parsed.ontId),
            olt = olt,
            board = parsed.slot,
            port = parsed.port,
            onuIndex = parsed.ontId,
            name = clipName(parsed.description),
            importedFromOlt = true,
            syncedAfterImport = false,
            createdAt = now,
            updatedAt = now
        )
        onu.status = OltMgrOnuStatusCurrent(
            onu = onu,
            runState = parsed.runState ?: "offline",
            matchState = parsed.matchState,
            distanceM = parsed.distanceM,
            lastDownCause = parsed.lastDownCause,
            polledAt = now
        )
        if (!parsed.lineProfileName.isNullOrBlank()) {
            onu.lineProfileName = parsed.lineProfileName.trim()
        }
        if (!parsed.serviceProfileName.isNullOrBlank()) {
            onu.serviceProfileName = parsed.serviceProfileName.trim()
        }
        return onu
    }

    private fun applyUpdate(
        olt: OltMgrOlt,
        onu: OltMgrOnu,
        parsed: ParsedOnuSummary,
        now: Instant,
        pending: PendingWrites
    ): UpdateOutcome {
        var onuChanged = false
        val wasDeleted = onu.deletedAt != null
        if (wasDeleted) {
            onu.deletedAt = null
            if (!hasApiOwnedFields(onu)) {
                onu.importedFromOlt = true
            }
            onuChanged = true
        }

        val positionChanged =
            onu.board != parsed.slot || onu.port != parsed.port || onu.onuIndex != parsed.ontId
        if (positionChanged) {
            onu.board = parsed.slot
            onu.port = parsed.port
            onu.onuIndex = parsed.ontId
            onu.externalId = externalId(parsed.slot, parsed.port, parsed.ontId)
            onuChanged = true
            pending.audits += OltMgrAuditLog(
                olt = olt,
                onu = onu,
                action = "sync_position_changed",
                source = "sync",
                details = """{"sn":"${onu.sn}","board":${parsed.slot},"port":${parsed.port},"onu_index":${parsed.ontId}}"""
            )
        }

        val canonicalSn = parsed.sn.trim().uppercase()
        if (onu.sn != canonicalSn &&
            HuaweiGponSnmpCodec.normalizeOntSn(onu.sn) == HuaweiGponSnmpCodec.normalizeOntSn(canonicalSn)
        ) {
            onu.sn = canonicalSn
            onuChanged = true
        }

        if (onu.importedFromOlt) {
            val desc = clipName(parsed.description)
            if (!desc.isNullOrBlank() && onu.name != desc) {
                onu.name = desc
                onuChanged = true
            }
            val lineProf = parsed.lineProfileName?.trim()?.takeIf { it.isNotEmpty() }
            if (lineProf != null && onu.lineProfileName != lineProf) {
                onu.lineProfileName = lineProf
                onuChanged = true
            }
            val srvProf = parsed.serviceProfileName?.trim()?.takeIf { it.isNotEmpty() }
            if (srvProf != null && onu.serviceProfileName != srvProf) {
                onu.serviceProfileName = srvProf
                onuChanged = true
            }
        }

        val statusChanged = applyStatus(onu, parsed, now, pending)

        if (!onuChanged && !statusChanged) {
            return UpdateOutcome.UNCHANGED
        }

        if (onuChanged) {
            onu.updatedAt = now
            pending.onus += onu
        }
        return UpdateOutcome.UPDATED
    }

    private fun applyStatus(
        onu: OltMgrOnu,
        parsed: ParsedOnuSummary,
        now: Instant,
        pending: PendingWrites
    ): Boolean {
        telemetryPublisher.get()?.publishEvent(OltStateObservation(onu.sn, parsed.runState, parsed.lastDownCause, now))
        val runState = parsed.runState ?: "offline"
        val matchState = parsed.matchState
        val distanceM = parsed.distanceM
        val lastDownCause = parsed.lastDownCause
        val status = onu.status
        if (status == null) {
            val created = OltMgrOnuStatusCurrent(
                onu = onu,
                runState = runState,
                matchState = matchState,
                distanceM = distanceM,
                lastDownCause = lastDownCause,
                polledAt = now
            )
            onu.status = created
            pending.statuses += created
            return true
        }
        if (status.runState == runState &&
            status.matchState == matchState &&
            (distanceM == null || status.distanceM == distanceM) &&
            (lastDownCause == null || status.lastDownCause == lastDownCause)
        ) {
            return false
        }
        status.runState = runState
        status.matchState = matchState
        if (distanceM != null) {
            status.distanceM = distanceM
        }
        if (lastDownCause != null) {
            status.lastDownCause = lastDownCause
        }
        status.polledAt = now
        pending.statuses += status
        return true
    }

    private fun hasApiOwnedFields(onu: OltMgrOnu): Boolean {
        return onu.zone != null ||
            onu.onuType != null ||
            onu.mainVlanId != null ||
            !onu.address.isNullOrBlank() ||
            !onu.contact.isNullOrBlank() ||
            onu.authorizationDate != null && !onu.importedFromOlt
    }

    private fun externalId(board: Int, port: Int, ontId: Int): String {
        return "${props().oltId}_${board}_${port}_$ontId"
    }

    private fun finish(result: SyncResult, startedAt: Instant): SyncResult {
        val finished = Instant.now()
        val withDuration = result.copy(durationMs = finished.toEpochMilli() - startedAt.toEpochMilli())
        lastResultRef.set(withDuration)
        syncRunRepository().save(
            OltMgrSyncRun(
                startedAt = startedAt,
                finishedAt = finished,
                inserted = withDuration.inserted,
                updated = withDuration.updated,
                softDeleted = withDuration.softDeleted,
                unchanged = withDuration.unchanged,
                skippedReason = withDuration.skippedReason,
                error = withDuration.error,
                durationMs = withDuration.durationMs
            )
        )
        return withDuration
    }

    private fun SyncResult.toDto(): SyncResultDto {
        return SyncResultDto(
            inserted = inserted,
            updated = updated,
            softDeleted = softDeleted,
            unchanged = unchanged,
            durationMs = durationMs,
            skippedReason = skippedReason,
            error = error
        )
    }

    private fun clipName(value: String?): String? {
        if (value == null) return null
        return if (value.length <= NAME_MAX) value else value.substring(0, NAME_MAX)
    }

    private data class PendingWrites(
        val onus: LinkedHashSet<OltMgrOnu> = linkedSetOf(),
        val statuses: LinkedHashSet<OltMgrOnuStatusCurrent> = linkedSetOf(),
        val audits: MutableList<OltMgrAuditLog> = mutableListOf()
    )

    private data class PositionMove(
        val onu: OltMgrOnu,
        val board: Int,
        val port: Int,
        val onuIndex: Int
    )

    private enum class UpdateOutcome {
        UPDATED,
        UNCHANGED
    }
}
