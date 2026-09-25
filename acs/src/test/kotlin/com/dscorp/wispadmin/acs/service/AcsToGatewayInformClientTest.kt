package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.config.AcsProperties
import com.dscorp.wispadmin.events.CpeInformPayload
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.web.client.RestTemplate
import java.time.Instant

class AcsToGatewayInformClientTest {

    private val properties = AcsProperties().apply {
        gateway.internalBaseUrl = "http://127.0.0.1:8080/ispadmin"
        gateway.apiKey = "stg-key"
        gateway.env = "stg"
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
    fun `informa al gateway por HTTP con el ambiente y no publica en el bus`() {
        val client = AcsToGatewayInformClient(properties, rest, json)
        val entity = slot<HttpEntity<String>>()

        client.postInform(payload())

        verify(exactly = 1) {
            rest.postForEntity(
                "http://127.0.0.1:8080/ispadmin/api/olt-gateway/acs/cpe-inform",
                capture(entity),
                String::class.java,
            )
        }
        assertEquals("stg-key", entity.captured.headers.getFirst(AcsToGatewayInformClient.HEADER))
        assertEquals("stg", entity.captured.headers.getFirst(AcsToGatewayInformClient.ENV_HEADER))
    }
}
