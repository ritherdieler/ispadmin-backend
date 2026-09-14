package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.dto.OltOnuRefDto
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class OltHealthOnuQueryService(
    private val onus: OltMgrOnuRepository,
    private val olts: OltMgrOltRepository
) {
    fun findBySn(sn: String): OltOnuRefDto? {
        val exact = onus.findBySnIgnoreCaseAndDeletedAtIsNull(sn).orElse(null)
        if (exact != null) return exact.toRef()
        val normalized = HuaweiGponSnmpCodec.normalizeOntSn(sn)
        if (!normalized.equals(sn, ignoreCase = true)) {
            val byNorm = onus.findBySnIgnoreCaseAndDeletedAtIsNull(normalized).orElse(null)
            if (byNorm != null) return byNorm.toRef()
        }
        val suffix = hexSuffix(sn) ?: return null
        return onus.findBySnSuffixIgnoreCaseAndDeletedAtIsNull(suffix).singleOrNull()?.toRef()
    }

    fun findByExternalId(externalId: String): OltOnuRefDto? {
        return onus.findByExternalIdAndDeletedAtIsNull(externalId).orElse(null)?.toRef()
    }

    fun findByOltBoardPortOnu(oltId: Long, board: Int, port: Int, onuIndex: Int): OltOnuRefDto? {
        return onus.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(oltId, board, port, onuIndex)
            .orElse(null)?.toRef()
    }

    fun findByOlt(oltId: Long): List<OltOnuRefDto> {
        return onus.findByOlt_IdWithStatus(oltId).filter { it.deletedAt == null }.map { it.toRef() }
    }

    fun findOltIdByName(name: String): Long? {
        return olts.findByName(name).orElse(null)?.id
    }

    private fun OltMgrOnu.toRef(): OltOnuRefDto = OltOnuRefDto(
        id = id!!,
        sn = sn,
        externalId = externalId,
        oltId = olt.id,
        oltName = olt.name,
        board = board,
        port = port,
        onuIndex = onuIndex,
        zoneId = zone?.id
    )

    private fun hexSuffix(serial: String): String? {
        val cleaned = serial.uppercase().filter { it.isLetterOrDigit() }
        if (cleaned.length < 6) return null
        val suffix = cleaned.takeLast(6)
        return suffix.takeIf { HEX_SUFFIX.matches(it) }
    }

    private companion object {
        val HEX_SUFFIX = Regex("^[0-9A-F]{6}$")
    }
}
