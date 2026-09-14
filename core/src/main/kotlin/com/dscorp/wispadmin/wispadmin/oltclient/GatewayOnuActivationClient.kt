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

    fun provision(request: GatewayCpeProvisionRequest): GatewayCpeProvisionResponse {
        val sn = request.sn.trim()
        val response = http.postJsonBody(
            "/api/olt-gateway/onus/$sn/cpe/provision",
            objectMapper.writeValueAsString(request),
        )
        val body = response.body ?: throw IllegalStateException("Empty CPE provision response")
        return objectMapper.readValue(body, GatewayCpeProvisionResponse::class.java)
    }

    fun setWifi(sn: String, request: GatewayCpeWifiRequest): GatewayCpeCommandResponse {
        val response = http.postJsonBody(
            "/api/olt-gateway/onus/$sn/cpe/wifi",
            objectMapper.writeValueAsString(request),
        )
        val body = response.body ?: return GatewayCpeCommandResponse()
        return objectMapper.readValue(body, GatewayCpeCommandResponse::class.java)
    }

    fun accessLayout(sn: String): GatewayCpeAccessLayout {
        val response = http.getJson("/api/olt-gateway/onus/$sn/cpe/access-layout")
        val body = response.body ?: throw IllegalStateException("Empty CPE access-layout response")
        return objectMapper.readValue(body, GatewayCpeAccessLayout::class.java)
    }

    fun servicePorts(sn: String): GatewayServicePortsDto {
        val response = http.getJson("/api/olt-gateway/onus/$sn/service-ports")
        val body = response.body ?: throw IllegalStateException("Empty service-ports response")
        return objectMapper.readValue(body, GatewayServicePortsDto::class.java)
    }

    fun removeServicePort(sn: String, vlan: Int): GatewayServicePortsDto {
        val response = http.postJsonBody(
            "/api/olt-gateway/onus/$sn/service-port/remove",
            objectMapper.writeValueAsString(GatewayRemoveServicePortRequest(vlan)),
        )
        val body = response.body ?: throw IllegalStateException("Empty remove service-port response")
        return objectMapper.readValue(body, GatewayServicePortsDto::class.java)
    }
}
