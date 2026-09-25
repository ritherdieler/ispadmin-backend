package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuFilter
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuPageDto

object ConfiguredOnuReservationMerge {
    private val stages = setOf("NONE", "CLAIMED", "AUTHORIZED", "READY")

    fun apply(
        inventory: List<ConfiguredOnuItemDto>,
        reservations: List<ProvisioningV2OnuOwnership>,
        filter: ConfiguredOnuFilter,
        page: Int,
        size: Int,
    ): ConfiguredOnuPageDto {
        val bySn = reservations.associateBy { it.serial.trim().uppercase() }
        val stagedInventory = inventory.map { item ->
            val stage = bySn[item.sn.trim().uppercase()]?.stage ?: "NONE"
            item.copy(reservationStage = stage)
        }
        val known = stagedInventory.map { it.sn.trim().uppercase() }.toSet()
        val orphans = if (inventoryOnlyFilter(filter)) {
            emptyList()
        } else {
            reservations
                .filter { it.serial.trim().uppercase() !in known }
                .filter { matchesOrphan(it, filter) }
                .map { toReservationItem(it) }
        }
        val stage = filter.reservationStage?.trim()?.uppercase()?.takeIf { it in stages }
        val merged = (stagedInventory + orphans)
            .filter { stage == null || it.reservationStage.equals(stage, ignoreCase = true) }
            .sortedWith(compareByDescending<ConfiguredOnuItemDto> { it.authorizationDate ?: "" }.thenBy { it.sn })
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        val from = safePage * safeSize
        val slice = if (from >= merged.size) emptyList() else merged.subList(from, minOf(from + safeSize, merged.size))
        val total = merged.size
        val pages = if (total == 0) 0 else (total + safeSize - 1) / safeSize
        return ConfiguredOnuPageDto(
            items = slice,
            page = safePage,
            size = safeSize,
            totalElements = total.toLong(),
            totalPages = pages,
        )
    }

    private fun inventoryOnlyFilter(filter: ConfiguredOnuFilter): Boolean =
        filter.runState != null ||
            filter.administrativeStatus != null ||
            filter.lastDownCause != null ||
            filter.signalCategory != null ||
            filter.oltId != null ||
            filter.zoneId != null ||
            filter.vlan != null ||
            filter.onuTypeId != null ||
            filter.onuTypeName != null ||
            filter.customProfile != null ||
            filter.ponType != null ||
            filter.mode != null ||
            filter.splitterId != null ||
            filter.configurationMethod != null ||
            filter.wanMode != null ||
            filter.mgmtIpMode != null ||
            filter.importedSynced != null ||
            filter.lastResyncFailed != null ||
            filter.lineProfileMaptype != null

    private fun matchesOrphan(ownership: ProvisioningV2OnuOwnership, filter: ConfiguredOnuFilter): Boolean {
        val q = filter.q?.trim()?.takeIf { it.isNotEmpty() }
        if (q != null && !ownership.serial.contains(q, ignoreCase = true)) return false
        if (filter.board != null && ownership.board != filter.board) return false
        if (filter.port != null && ownership.port != filter.port) return false
        return true
    }

    private fun toReservationItem(ownership: ProvisioningV2OnuOwnership) = ConfiguredOnuItemDto(
        id = 0L,
        sn = ownership.serial.trim().uppercase(),
        externalId = "",
        board = ownership.board ?: 0,
        port = ownership.port ?: 0,
        onuIndex = ownership.ontId ?: 0,
        name = null,
        importedFromOlt = false,
        runState = null,
        matchState = null,
        polledAt = null,
        reservationStage = ownership.stage,
    )
}
