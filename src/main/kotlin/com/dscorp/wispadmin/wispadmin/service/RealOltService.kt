package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response
import com.dscorp.wispadmin.wispadmin.response.UnconfirmedOnuResponse
import com.dscorp.wispadmin.wispadmin.util.OltHttpClient
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

@Service
class RealOltService(
    private val oltHttpClient: OltHttpClient
) : OltService {

    override fun getUnConfiguredOnus(): List<Response>? {
        return try {
            oltHttpClient.get("onu/unconfigured_onus", UnconfirmedOnuResponse::class.java).response
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun getOnuBySn(onuSn: String): OnuBySnResponse {
        return oltHttpClient.get("onu/get_onus_details_by_sn/${onuSn}", responseType = OnuBySnResponse::class.java)
    }

    override fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox) {
        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        body.apply {
            add("olt_id", newNapBox.oltId)
            add("board", newNapBox.oltBoard)
            add("port", newNapBox.oltPort)
        }
        oltHttpClient.post("onu/move/${onu.sn}", body, Any::class.java)
    }

    override fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest) {
        with(authorizationRequest) {
            val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
            body.add("olt_id", olt_id)
            body.add("pon_type", pon_type)
            body.add("board", board)
            body.add("port", port)
            body.add("sn", sn)
            body.add("vlan", vlan)
            body.add("onu_type", onu_type)
            body.add("zone", zone)
            body.add("name", name)
            body.add("onu_mode", onu_mode)
            body.add("custom_profile", custom_profile)

            oltHttpClient.post("onu/authorize_onu", body, Any::class.java)
        }
    }

    override fun deleteOnu(onuExternalId: String) {
        oltHttpClient.post("onu/delete/${onuExternalId}", null, Any::class.java)
    }

    override fun rebootOnu(uniqueExternalId: String) {
        oltHttpClient.post("onu/reboot/${uniqueExternalId}", null, Any::class.java)
    }
}
