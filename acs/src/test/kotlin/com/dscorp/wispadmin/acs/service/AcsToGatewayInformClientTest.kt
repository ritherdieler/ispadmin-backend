package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.config.AcsProperties
import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.events.EventBusPort
import com.dscorp.wispadmin.events.NoOpEventBus
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.events.RecordingEventBus
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.RestTemplate
import java.time.Instant

class AcsToGatewayInformClientTest {

    private val properties = AcsProperties().apply {
        gateway.internalBaseUrl = "http://127.0.0.1:8080/ispadmin"
        gateway.apiKey = "k"
    }
    private val rest = mockk<RestTemplate>(relaxed = true)
    private val json = ObjectMapper().findAndRegisterModules()

    private fun payload() = CpeInformPayload(
        sn = "ZTEGDC47BFFD",
        deviceId = "dev-1",
        informAt = Instant.parse("2026-09-16T21:00:00Z"),
        observedAt = Instant.parse("2026-09-16T21:00:00Z"),
        complete = true,
        qualityStatus = "FRESH",
    )

    @Test
    fun `publica cpe inform en el bus cuando no es no-op`() {
        val bus = RecordingEventBus()
        val provider = mockk<ObjectProvider<EventBusPort>>()
        every { provider.ifAvailable } returns bus
        val client = AcsToGatewayInformClient(properties, rest, json, provider)

        client.postInform(payload())

        assertEquals(1, bus.published.count { it.type == PlatformEventTypes.CPE_INFORM })
        assertEquals("ZTEGDC47BFFD", bus.published.single().sn)
        verify(exactly = 0) { rest.postForEntity(any<String>(), any(), String::class.java) }
    }

    @Test
    fun `cae a HTTP si el bus es no-op`() {
        val provider = mockk<ObjectProvider<EventBusPort>>()
        every { provider.ifAvailable } returns NoOpEventBus()
        val client = AcsToGatewayInformClient(properties, rest, json, provider)

        client.postInform(payload())

        verify(exactly = 1) {
            rest.postForEntity(
                "http://127.0.0.1:8080/ispadmin/api/olt-gateway/acs/cpe-inform",
                any(),
                String::class.java,
            )
        }
    }
}
