package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.config.AcsProperties
import com.dscorp.wispadmin.events.CpeInformPayload
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class AcsToGatewayInformClient(
    private val properties: AcsProperties,
    @Qualifier("acsGatewayRestTemplate") private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(AcsToGatewayInformClient::class.java)

    fun postInform(payload: CpeInformPayload) {
        val base = properties.gateway.internalBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) {
            log.warn("ACS gateway URL blank; skip cpe.inform POST for sn={}", payload.sn)
            return
        }
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set(HEADER, properties.gateway.apiKey)
        val body = objectMapper.writeValueAsString(payload)
        restTemplate.postForEntity("$base/api/olt-gateway/acs/cpe-inform", HttpEntity(body, headers), String::class.java)
    }

    companion object {
        const val HEADER = "X-Acs-To-Gateway-Key"
    }
}
