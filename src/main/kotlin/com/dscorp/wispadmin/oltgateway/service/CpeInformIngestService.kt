package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.events.EventBusPort
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class CpeInformIngestService(
    private val eventBus: EventBusPort,
    private val objectMapper: ObjectMapper,
) {
    fun ingest(payload: CpeInformPayload) {
        eventBus.publish(
            PlatformEvent(
                type = PlatformEventTypes.CPE_INFORM,
                sn = payload.sn,
                occurredAt = payload.informAt,
                payloadJson = objectMapper.writeValueAsString(payload),
                producer = "oltgateway",
            )
        )
    }
}
