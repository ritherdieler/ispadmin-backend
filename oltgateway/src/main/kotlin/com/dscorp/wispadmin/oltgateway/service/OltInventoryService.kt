package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuAutofind
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuAutofindRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.port.OltAutofindSnapshot
import com.dscorp.wispadmin.oltgateway.port.OltInventoryPort
import com.dscorp.wispadmin.oltgateway.port.OltOnuSnapshot
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import com.dscorp.wispadmin.transport.SerialSuffix
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class OltInventoryService(
    private val onus: OltMgrOnuRepository,
    private val olts: OltMgrOltRepository,
    private val autofind: OltMgrOnuAutofindRepository
) : OltInventoryPort {

    override fun findBySn(sn: String): OltOnuSnapshot? {
        val exact = onus.findBySnIgnoreCaseAndDeletedAtIsNull(sn).orElse(null)
        if (exact != null) return exact.toSnapshot()
        val normalized = HuaweiGponSnmpCodec.normalizeOntSn(sn)
        if (!normalized.equals(sn, ignoreCase = true)) {
            val byNorm = onus.findBySnIgnoreCaseAndDeletedAtIsNull(normalized).orElse(null)
            if (byNorm != null) return byNorm.toSnapshot()
        }
        val suffix = SerialSuffix.normalizeSuffix(sn) ?: return null
        return onus.findBySnSuffixIgnoreCaseAndDeletedAtIsNull(suffix).singleOrNull()?.toSnapshot()
    }

    override fun findByExternalId(externalId: String): OltOnuSnapshot? =
        onus.findByExternalIdAndDeletedAtIsNull(externalId).orElse(null)?.toSnapshot()

    override fun findBySlot(oltId: Long, board: Int, port: Int, onuIndex: Int): OltOnuSnapshot? =
        onus.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(oltId, board, port, onuIndex)
            .orElse(null)?.toSnapshot()

    override fun listConfigured(oltId: Long): List<OltOnuSnapshot> =
        onus.findByOlt_IdWithStatus(oltId).filter { it.deletedAt == null }.map { it.toSnapshot() }

    override fun listAutofind(oltId: Long?): List<OltAutofindSnapshot> =
        autofind.findAllByOrderByLastSeenAtDesc().map { it.toSnapshot() }

    override fun countConfigured(): Long = onus.countByDeletedAtIsNull()

    override fun findOltIdByName(name: String): Long? =
        olts.findByName(name).orElse(null)?.id

    private fun OltMgrOnu.toSnapshot(): OltOnuSnapshot = OltOnuSnapshot(
        id = id!!,
        sn = sn,
        externalId = externalId,
        oltId = olt.id!!,
        oltName = olt.name,
        board = board,
        port = port,
        onuIndex = onuIndex,
        onuTypeName = onuTypeName,
        zoneId = zone?.id,
        runState = status?.runState,
        lastDownCause = status?.lastDownCause,
        onuRxDbm = status?.onuRxDbm,
        oltRxDbm = status?.oltRxDbm,
        onuTxDbm = status?.onuTxDbm,
        temperatureC = status?.temperatureC,
        distanceM = status?.distanceM,
        polledAt = status?.polledAt
    )

    private fun OltMgrOnuAutofind.toSnapshot(): OltAutofindSnapshot = OltAutofindSnapshot(
        sn = sn,
        frame = frame,
        board = board,
        port = port,
        ponType = ponType,
        vendorId = vendorId,
        equipmentId = equipmentId,
        lastSeenAt = lastSeenAt
    )
}
