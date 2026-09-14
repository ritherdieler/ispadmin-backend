package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.events.EventBusPort
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.events.RecordingEventBus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class CpeInformIngestServiceTest {
    private val bus = RecordingEventBus()
    private val json = ObjectMapper().registerModule(JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val service = CpeInformIngestService(bus, json)

    @Test
    fun `publishes enriched cpe inform without parsing wifi tree`() {
        val informAt = Instant.parse("2026-09-08T18:00:00Z")
        val payload = CpeInformPayload(
            sn = "12345B4641531C0B6",
            deviceId = "B46415-V2804AX15T-12345B4641531C0B6",
            informAt = informAt,
            model = "V2804AX15T",
            associatedDeviceCount = 0,
            associated2g = 0,
            associated5g = 0,
            qualityStatus = "FRESH",
            complete = true,
            observedAt = informAt,
            stations = emptyList(),
        )
        service.ingest(payload)
        assertEquals(1, bus.published.size)
        val event = bus.published.single()
        assertEquals(PlatformEventTypes.CPE_INFORM, event.type)
        assertEquals("12345B4641531C0B6", event.sn)
        assertEquals(informAt, event.occurredAt)
        assertTrue(event.payloadJson.contains("\"associatedDeviceCount\":0"))
        assertTrue(event.payloadJson.contains("\"stations\":[]"))
    }
}
