package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class OnuService @Autowired constructor(
    private val oltService: OltService
) {

    fun getUnConfiguredOnus(): List<Response>? {
        return oltService.getUnConfiguredOnus()
    }

    fun getOnuBySn(onuSn: String): OnuBySnResponse {
        return oltService.getOnuBySn(onuSn)
    }

    fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox) {
        oltService.moveOnu(request, onu, newNapBox)
    }

    fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest) {
        oltService.authorizeOnuInSmartOltWidthPostMethod(authorizationRequest)
    }

    fun deleteOnu(onuExternalId: String) {
        oltService.deleteOnu(onuExternalId)
    }

    fun rebootOnuBySn(onuSn: String) {
        val details = getOnuBySn(onuSn)
        if (details.onus.isEmpty()) {
            throw IllegalArgumentException("No se encontró la ONU en SmartOLT para el serial indicado")
        }
        val uniqueId = details.onus[0].unique_external_id
        if (uniqueId.isBlank()) {
            throw IllegalStateException("La ONU no tiene identificador externo en SmartOLT")
        }
        oltService.rebootOnu(uniqueId)
    }
}