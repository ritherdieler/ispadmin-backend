package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import com.dscorp.wispadmin.wispadmin.oltclient.OnuSerialNormalizer
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response
import com.dscorp.wispadmin.wispadmin.response.UnconfirmedOnuResponse
import com.dscorp.wispadmin.wispadmin.util.SmartOltHttpClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

@Service
class RealOltService(
    @Value("\${olt.gateway.client-enabled:false}") private val gatewayClientEnabled: Boolean,
    private val gatewayHttp: ObjectProvider<OltGatewayHttpClient>,
    private val objectMapper: ObjectMapper,
    private val smartOltHttpClient: SmartOltHttpClient,
) : OltService {

    override fun getUnConfiguredOnus(): List<Response>? {
        val client = gatewayClient()
        if (client != null) {
            return try {
                fetchUnconfiguredFromGateway(client)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
        return try {
            smartOltHttpClient.get("onu/unconfigured_onus", UnconfirmedOnuResponse::class.java).response
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun getOnuBySn(onuSn: String): OnuBySnResponse {
        val client = gatewayClient()
        if (client != null) {
            val body = client.getJson("/api/olt-gateway/onu/get_onus_details_by_sn/${encode(onuSn)}").body
                ?: throw IllegalStateException("Empty ONU by-sn response")
            return objectMapper.readValue(body, OnuBySnResponse::class.java)
        }
        return smartOltHttpClient.get("onu/get_onus_details_by_sn/${onuSn}", OnuBySnResponse::class.java)
    }

    override fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox) {
        val client = gatewayClient()
        if (client != null) {
            client.postForm("/api/olt-gateway/onu/move/${encode(onu.sn)}", moveForm(newNapBox))
            return
        }
        smartOltHttpClient.post("onu/move/${onu.sn}", moveForm(newNapBox), Any::class.java)
    }

    override fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest) {
        val client = gatewayClient()
        if (client != null) {
            client.postForm("/api/olt-gateway/onu/authorize_onu", authorizeForm(authorizationRequest))
            return
        }
        smartOltHttpClient.post("onu/authorize_onu", authorizeForm(authorizationRequest), Any::class.java)
    }

    override fun deleteOnu(onuExternalId: String) {
        val client = gatewayClient()
        if (client != null) {
            client.postJson("/api/olt-gateway/onu/delete/${encode(onuExternalId)}")
            return
        }
        smartOltHttpClient.post("onu/delete/${onuExternalId}", null, Any::class.java)
    }

    override fun rebootOnu(uniqueExternalId: String) {
        val client = gatewayClient()
        if (client != null) {
            client.postJson("/api/olt-gateway/onu/reboot/${encode(uniqueExternalId)}")
            return
        }
        smartOltHttpClient.post("onu/reboot/${uniqueExternalId}", null, Any::class.java)
    }

    private fun gatewayClient(): OltGatewayHttpClient? =
        if (gatewayClientEnabled) gatewayHttp.ifAvailable else null

    private fun fetchUnconfiguredFromGateway(client: OltGatewayHttpClient): List<Response> {
        val body = client.getJson("/api/olt-gateway/onu/unconfigured_onus").body ?: return emptyList()
        val parsed = objectMapper.readValue(body, UnconfirmedOnuResponse::class.java)
        return parsed.response.map { item ->
            item.copy(sn = OnuSerialNormalizer.preferredSn(item.sn))
        }
    }

    private fun authorizeForm(request: OnuAuthorizationRequest): MultiValueMap<String, String> {
        val body = LinkedMultiValueMap<String, String>()
        body.add("olt_id", request.olt_id)
        body.add("pon_type", request.pon_type)
        body.add("board", request.board)
        body.add("port", request.port)
        body.add("sn", request.sn)
        body.add("vlan", request.vlan)
        body.add("onu_type", request.onu_type)
        body.add("zone", request.zone)
        body.add("name", request.name)
        body.add("onu_mode", request.onu_mode)
        body.add("custom_profile", request.custom_profile)
        return body
    }

    private fun moveForm(newNapBox: NapBox): MultiValueMap<String, String> {
        val body = LinkedMultiValueMap<String, String>()
        body.add("olt_id", newNapBox.oltId?.toString().orEmpty())
        body.add("board", newNapBox.oltBoard?.toString().orEmpty())
        body.add("port", newNapBox.oltPort?.toString().orEmpty())
        return body
    }

    private fun encode(value: String): String =
        UriUtils.encodePathSegment(value, StandardCharsets.UTF_8)
}
