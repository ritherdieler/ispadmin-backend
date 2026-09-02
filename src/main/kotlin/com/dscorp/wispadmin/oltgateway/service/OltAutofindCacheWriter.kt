package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredItemDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuAutofind
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuAutofindRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

open class OltAutofindCacheWriter(
    private val autofindRepository: OltMgrOnuAutofindRepository,
    private val onuRepository: OltMgrOnuRepository,
    private val properties: OltGatewayProperties
) {

    data class StoreResult(val stored: Int, val removed: Int)

    @Transactional
    open fun replaceSnapshot(items: List<ParsedAutofindOnt>, source: String): StoreResult {
        val now = Instant.now()
        val seen = linkedMapOf<String, ParsedAutofindOnt>()
        items.forEach { item ->
            val sn = HuaweiGponSnmpCodec.normalizeOntSn(item.sn)
            if (sn.isNotBlank()) seen[sn.uppercase()] = item
        }

        val removed = if (seen.isEmpty()) {
            val total = autofindRepository.count().toInt()
            autofindRepository.deleteAllInBatch()
            total
        } else {
            autofindRepository.deleteBySnUpperNotIn(seen.keys)
        }

        seen.forEach { (normalizedSn, item) ->
            val existing = autofindRepository.findBySnIgnoreCase(normalizedSn).orElse(null)
            val row = existing ?: OltMgrOnuAutofind(sn = normalizedSn, firstSeenAt = now)
            row.sn = normalizedSn
            row.frame = item.frame
            row.board = item.slot
            row.port = item.port
            row.ponType = "gpon"
            row.vendorId = item.vendorId
            row.equipmentId = item.equipmentId
            row.softwareVersion = item.softwareVersion
            row.autofindTime = item.autofindTime
            row.source = source
            row.lastSeenAt = now
            autofindRepository.save(row)
        }
        return StoreResult(stored = seen.size, removed = removed)
    }

    @Transactional(readOnly = true)
    open fun listUnconfigured(): List<SmartOltUnconfiguredItemDto> {
        val cached = autofindRepository.findAllByOrderByLastSeenAtDesc()
        if (cached.isEmpty()) return emptyList()
        val configured = onuRepository
            .findExistingSnsUpper(cached.map { it.sn.uppercase() })
            .toHashSet()
        return cached
            .filterNot { configured.contains(it.sn.uppercase()) }
            .map { row ->
                SmartOltUnconfiguredItemDto(
                    board = row.board.toString(),
                    olt_id = properties.oltId,
                    onu = "",
                    onu_type_id = "",
                    onu_type_name = row.equipmentId.orEmpty(),
                    pon_type = row.ponType,
                    port = row.port.toString(),
                    sn = row.sn
                )
            }
    }

    @Transactional(readOnly = true)
    open fun cachedCount(): Long = autofindRepository.count()
}
