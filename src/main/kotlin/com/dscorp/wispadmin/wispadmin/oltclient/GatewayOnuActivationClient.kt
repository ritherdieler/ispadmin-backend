package com.dscorp.wispadmin.wispadmin.oltclient

import com.fasterxml.jackson.databind.ObjectMapper

class GatewayOnuActivationClient(
    private val http: OltGatewayHttpClient,
    private val objectMapper: ObjectMapper,
) {
    fun activate(request: GatewayOnuActivateRequest): GatewayOnuActivateResponse {
        val response = http.postJsonBody("/api/olt-gateway/onu/activate", objectMapper.writeValueAsString(request))
        val body = response.body ?: throw IllegalStateException("Empty activate response")
        return objectMapper.readValue(body, GatewayOnuActivateResponse::class.java)
    }

    fun activationBySn(sn: String): GatewayOnuActivateResponse {
        val response = http.getJson("/api/olt-gateway/onus/by-sn/$sn/activation")
        val body = response.body ?: throw IllegalStateException("Empty activation response")
        return objectMapper.readValue(body, GatewayOnuActivateResponse::class.java)
    }

    fun reboot(sn: String): GatewayCpeCommandResponse {
        val response = http.postJson("/api/olt-gateway/onus/$sn/cpe/reboot")
        val body = response.body ?: return GatewayCpeCommandResponse()
        return objectMapper.readValue(body, GatewayCpeCommandResponse::class.java)
    }

    fun wifiRefresh(sn: String): GatewayCpeCommandResponse {
        val response = http.postJson("/api/olt-gateway/onus/$sn/cpe/wifi-refresh")
        val body = response.body ?: return GatewayCpeCommandResponse()
        return objectMapper.readValue(body, GatewayCpeCommandResponse::class.java)
    }

    fun telemetry(sn: String): GatewayCpeTelemetry? {
        return try {
            val response = http.getJson("/api/olt-gateway/onus/$sn/cpe/telemetry")
            val body = response.body ?: return null
            objectMapper.readValue(body, GatewayCpeTelemetry::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
