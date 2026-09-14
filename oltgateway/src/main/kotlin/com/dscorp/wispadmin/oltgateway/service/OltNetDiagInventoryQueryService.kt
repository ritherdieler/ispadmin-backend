package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.dto.OltPonOnuDto
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class OltNetDiagInventoryQueryService(
    private val oltRepository: OltMgrOltRepository,
    private val onuRepository: OltMgrOnuRepository
) {
    fun findOltId(name: String): Long? {
        return oltRepository.findByName(name).orElse(null)?.id
    }

    fun listOnusOnPon(oltName: String, board: Int, port: Int): List<OltPonOnuDto> {
        val oltPk = findOltId(oltName) ?: return emptyList()
        return onuRepository.findByOlt_IdAndBoardAndPortWithStatus(oltPk, board, port).map { it.toSummary() }
    }

    fun findOnu(oltName: String, board: Int, port: Int, onuIndex: Int): OltPonOnuDto? {
        val oltPk = findOltId(oltName) ?: return null
        return onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(
            oltPk,
            board,
            port,
            onuIndex
        ).orElse(null)?.toSummary()
    }

    private fun OltMgrOnu.toSummary(): OltPonOnuDto = OltPonOnuDto(
        onuIndex = onuIndex,
        sn = sn,
        runState = status?.runState,
        lastDownCause = status?.lastDownCause,
        onuRxDbm = status?.onuRxDbm?.toDouble()
    )
}
