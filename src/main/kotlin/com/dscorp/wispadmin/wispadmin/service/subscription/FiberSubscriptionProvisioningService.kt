package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import org.springframework.stereotype.Service

@Service
class FiberSubscriptionProvisioningService {

    fun resolveHostDeviceId(requestHostDeviceId: Int, napBox: NapBox?): Int {
        napBox?.hostDevice?.id?.let { return it }
        if (requestHostDeviceId > 0) return requestHostDeviceId
        throw IllegalArgumentException("El dispositivo host es requerido")
    }

    fun enrichOnuFromNapBox(onu: OnuDto, napBox: NapBox): OnuDto {
        return onu.copy(
            board = napBox.oltBoard?.toString()?.takeIf { it.isNotBlank() } ?: onu.board,
            port = napBox.oltPort?.toString()?.takeIf { it.isNotBlank() } ?: onu.port,
            olt_id = napBox.oltId?.toString()?.takeIf { it.isNotBlank() } ?: onu.olt_id
        )
    }
}
