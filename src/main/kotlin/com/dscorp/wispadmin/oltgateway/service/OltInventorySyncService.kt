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
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

open class OltInventorySyncService(
    private val queryFacade: OltGatewayQueryFacade,
    private val oltRepository: OltMgrOltRepository,
    private val onuRepository: OltMgrOnuRepository,
    private val statusRepository: OltMgrOnuStatusCurrentRepository,
    private val auditLogRepository: OltMgrAuditLogRepository,
    private val syncRunRepository: OltMgrSyncRunRepository,
    private val taskRepository: OltMgrTaskRepository,
    private val properties: OltGatewayProperties,
    private val cliBus: OltCliBus? = null
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OltInventorySyncService::class.java)
        private const val NAME_MAX = 512
        private val running = AtomicBoolean(false)
    }

    @Volatile
    private var lastStartedAt: Instant? = null

    @Volatile
    private var lastResult: SyncResult? = null

    fun isRunning(): Boolean = running.get()

    fun lastStartedAt(): Instant? = lastStartedAt

    fun lastResult(): SyncResult? = lastResult

    fun status(): SyncStatusDto {
        return SyncStatusDto(
            running = running.get(),
            lastStartedAt = lastStartedAt?.toString(),
            lastResult = lastResult?.toDto(),
            busQueueDepth = cliBus?.queueDepth() ?: 0,
            busBusyJobType = cliBus?.busyJobType()?.name
        )
    }

    @Transactional(readOnly = true)
    open fun listConfigured(page: Int, size: Int): ConfiguredOnuPageDto {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200))
        val result = onuRepository.findByDeletedAtIsNull(pageable)
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

    fun syncInventory(): SyncResult {
        if (!running.compareAndSet(false, true)) {
            return SyncResult(skippedReason = "sync_already_running").also { lastResult = it }
        }
        val startedAt = Instant.now()
        lastStartedAt = startedAt
        try {
            if (properties.sync.skipWhenWriteRunning && taskRepository.existsByStatus("running")) {
                return finish(
                    SyncResult(skippedReason = "write_task_running"),
                    startedAt
                )
            }
            val olt = oltRepository.findByName(properties.oltId)
                .orElseThrow { IllegalStateException("OLT seed missing for ${properties.oltId}") }
            val snapshot = queryFacade.listOnusParsed()
            val bySn = snapshot.associateBy { it.sn.uppercase() }
            val existing = onuRepository.findByOlt_Id(olt.id!!)
            val existingBySn = existing.associateBy { it.sn.uppercase() }

            var inserted = 0
            var updated = 0
            var unchanged = 0
            var softDeleted = 0

            for (parsed in snapshot) {
                val current = existingBySn[parsed.sn.uppercase()]
                if (current == null) {
                    insertOnu(olt, parsed)
                    inserted++
                } else {
                    when (applyUpdate(olt, current, parsed)) {
                        UpdateOutcome.UPDATED -> updated++
                        UpdateOutcome.UNCHANGED -> unchanged++
                    }
                }
            }

            for (onu in existing) {
                if (onu.deletedAt != null) continue
                if (!bySn.containsKey(onu.sn.uppercase())) {
                    onu.deletedAt = Instant.now()
                    onu.updatedAt = Instant.now()
                    onuRepository.save(onu)
                    auditLogRepository.save(
                        OltMgrAuditLog(
                            olt = olt,
                            onu = onu,
                            action = "sync_missing_on_olt",
                            source = "sync",
                            details = """{"sn":"${onu.sn}"}"""
                        )
                    )
                    softDeleted++
                }
            }

            return finish(
                SyncResult(
                    inserted = inserted,
                    updated = updated,
                    softDeleted = softDeleted,
                    unchanged = unchanged
                ),
                startedAt
            )
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

    private fun insertOnu(olt: OltMgrOlt, parsed: ParsedOnuSummary) {
        val now = Instant.now()
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
        val saved = onuRepository.save(onu)
        val status = OltMgrOnuStatusCurrent(
            onu = saved,
            runState = parsed.runState ?: "offline",
            matchState = parsed.matchState,
            polledAt = now
        )
        saved.status = status
        statusRepository.save(status)
    }

    private fun applyUpdate(olt: OltMgrOlt, onu: OltMgrOnu, parsed: ParsedOnuSummary): UpdateOutcome {
        val now = Instant.now()
        var changed = false
        val wasDeleted = onu.deletedAt != null
        if (wasDeleted) {
            onu.deletedAt = null
            if (!hasApiOwnedFields(onu)) {
                onu.importedFromOlt = true
            }
            changed = true
        }

        val positionChanged =
            onu.board != parsed.slot || onu.port != parsed.port || onu.onuIndex != parsed.ontId
        if (positionChanged) {
            onu.board = parsed.slot
            onu.port = parsed.port
            onu.onuIndex = parsed.ontId
            onu.externalId = externalId(parsed.slot, parsed.port, parsed.ontId)
            changed = true
            auditLogRepository.save(
                OltMgrAuditLog(
                    olt = olt,
                    onu = onu,
                    action = "sync_position_changed",
                    source = "sync",
                    details = """{"sn":"${onu.sn}","board":${parsed.slot},"port":${parsed.port},"onu_index":${parsed.ontId}}"""
                )
            )
        }

        if (onu.importedFromOlt) {
            val desc = clipName(parsed.description)
            if (!desc.isNullOrBlank() && onu.name != desc) {
                onu.name = desc
                changed = true
            }
        }

        onu.updatedAt = now
        onuRepository.save(onu)

        val statusChanged = upsertStatus(onu, parsed, now)
        if (statusChanged) {
            changed = true
        }

        return if (changed) UpdateOutcome.UPDATED else UpdateOutcome.UNCHANGED
    }

    private fun upsertStatus(onu: OltMgrOnu, parsed: ParsedOnuSummary, now: Instant): Boolean {
        val runState = parsed.runState ?: "offline"
        val matchState = parsed.matchState
        val existing = statusRepository.findById(onu.id!!)
        if (existing.isPresent) {
            val status = existing.get()
            val changed = status.runState != runState || status.matchState != matchState
            status.runState = runState
            status.matchState = matchState
            status.polledAt = now
            statusRepository.save(status)
            return changed
        }
        val status = OltMgrOnuStatusCurrent(
            onu = onu,
            runState = runState,
            matchState = matchState,
            polledAt = now
        )
        onu.status = status
        statusRepository.save(status)
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
        return "${properties.oltId}_${board}_${port}_$ontId"
    }

    private fun finish(result: SyncResult, startedAt: Instant): SyncResult {
        val finished = Instant.now()
        val withDuration = result.copy(durationMs = finished.toEpochMilli() - startedAt.toEpochMilli())
        lastResult = withDuration
        syncRunRepository.save(
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

    private enum class UpdateOutcome {
        UPDATED,
        UNCHANGED
    }
}
