package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.config.AcsProperties
import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.events.EventBusPort
import com.dscorp.wispadmin.events.NoOpEventBus
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
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
    private val eventBus: ObjectProvider<EventBusPort>,
) {
    private val log = LoggerFactory.getLogger(AcsToGatewayInformClient::class.java)

    fun postInform(payload: CpeInformPayload) {
        val bus = eventBus.ifAvailable
        if (bus != null && bus !is NoOpEventBus) {
            bus.publish(
                PlatformEvent(
                    type = PlatformEventTypes.CPE_INFORM,
                    sn = payload.sn,
                    occurredAt = payload.informAt,
                    payloadJson = objectMapper.writeValueAsString(payload),
                    producer = "acs",
                )
            )
            return
        }
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
