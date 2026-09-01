package com.dscorp.wispadmin.wispadmin.service.onu

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.service.OltService
import org.springframework.stereotype.Service

@Service
class LegacyOnuOperations(
    private val oltService: OltService
) : OnuOperationsPort {

    override fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox) {
        oltService.moveOnu(request, onu, newNapBox)
    }

    override fun deleteOnu(onuExternalId: String) {
        oltService.deleteOnu(onuExternalId)
    }

    override fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest) {
        oltService.authorizeOnuInSmartOltWidthPostMethod(authorizationRequest)
    }

    override fun deleteOnuBySn(onuSn: String) {
        oltService.deleteOnu(resolveExternalId(onuSn))
    }

    override fun rebootOnuBySn(onuSn: String) {
        oltService.rebootOnu(resolveExternalId(onuSn))
    }

    private fun resolveExternalId(onuSn: String): String {
        val details = oltService.getOnuBySn(onuSn)
        if (details.onus.isEmpty()) {
            throw IllegalArgumentException("No se encontró la ONU en SmartOLT para el serial indicado")
        }
        val uniqueId = details.onus[0].unique_external_id
        if (uniqueId.isBlank()) {
            throw IllegalStateException("La ONU no tiene identificador externo en SmartOLT")
        }
        return uniqueId
    }
}
