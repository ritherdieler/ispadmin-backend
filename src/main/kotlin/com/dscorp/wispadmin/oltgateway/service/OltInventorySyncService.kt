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
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrSyncRunRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuPageDto
import com.dscorp.wispadmin.oltgateway.dto.SyncResultDto
import com.dscorp.wispadmin.oltgateway.dto.SyncStatusDto
import com.dscorp.wispadmin.oltgateway.exception.CliBusBusyException
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
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
    transactionTemplate: TransactionTemplate? = null
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltInventorySyncService::class.java)
        private const val NAME_MAX = 512
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
    }

    init {
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
    open fun listConfigured(page: Int, size: Int): ConfiguredOnuPageDto {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200))
        val result = onuRepository().findByDeletedAtIsNull(pageable)
        val items = result.content.map { onu ->
            val status = onu.status
            ConfiguredOnuItemDto(
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
                signalCategory = status?.signalCategory
            )
        }
        return ConfiguredOnuPageDto(
            items = items,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    open fun syncInventory(): SyncResult {
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
            val snapshot = queryFacade().listOnusParsed()
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
        } catch (ex: Exception) {
            logger.warn("Inventory sync failed: {}", ex.message)
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
        val bySn = snapshot.associateBy { it.sn.uppercase() }
        val existing = onuRepository().findByOlt_IdWithStatus(olt.id!!)
        val existingBySn = existing.associateBy { it.sn.uppercase() }
        val pending = PendingWrites()

        var inserted = 0
        var updated = 0
        var unchanged = 0
        var softDeleted = 0

        for (parsed in snapshot) {
            val current = existingBySn[parsed.sn.uppercase()]
            if (current == null) {
                pending.onus += buildNewOnu(olt, parsed, now)
                inserted++
            } else {
                when (applyUpdate(olt, current, parsed, now, pending)) {
                    UpdateOutcome.UPDATED -> updated++
                    UpdateOutcome.UNCHANGED -> unchanged++
                }
            }
        }

        for (onu in existing) {
            if (onu.deletedAt != null) continue
            if (!bySn.containsKey(onu.sn.uppercase())) {
                onu.deletedAt = now
                onu.updatedAt = now
                pending.onus += onu
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

        flushPending(pending)
        logger.info(
            "Inventory sync persisted onus={} statuses={} audits={}",
            pending.onus.size,
            pending.statuses.size,
            pending.audits.size
        )
        return SyncResult(
            inserted = inserted,
            updated = updated,
            softDeleted = softDeleted,
            unchanged = unchanged
        )
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
            polledAt = now
        )
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

        if (onu.importedFromOlt) {
            val desc = clipName(parsed.description)
            if (!desc.isNullOrBlank() && onu.name != desc) {
                onu.name = desc
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
        val runState = parsed.runState ?: "offline"
        val matchState = parsed.matchState
        val status = onu.status
        if (status == null) {
            val created = OltMgrOnuStatusCurrent(
                onu = onu,
                runState = runState,
                matchState = matchState,
                polledAt = now
            )
            onu.status = created
            pending.statuses += created
            return true
        }
        if (status.runState == runState && status.matchState == matchState) {
            return false
        }
        status.runState = runState
        status.matchState = matchState
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

    private enum class UpdateOutcome {
        UPDATED,
        UNCHANGED
    }
}
