package com.dscorp.wispadmin.servicehealth.client

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.time.Instant

class HealthOpticalPaginationTest {
    @Test
    fun `reads every page preserving original observation time`() {
        val client = HealthOltGatewayHttpClient("http://gateway", "key")
        val field = client.javaClass.getDeclaredField("restTemplate").apply { isAccessible = true }
        val server = MockRestServiceServer.createServer(field.get(client) as RestTemplate)
        for (page in 0..2) {
            val count = if (page == 2) 50 else 200
            val items = (0 until count).joinToString(",") { i -> """{"oltId":1,"sn":"SN${page * 200 + i}","board":1,"port":1,"onuIndex":${page * 200 + i},"onuRxDbm":-20,"polledAt":"2026-09-01T10:00:00Z"}""" }
            server.expect(requestTo("http://gateway/api/olt-gateway/onus/configured?size=200&page=$page"))
                .andRespond(withSuccess("""{"items":[$items],"totalPages":3,"totalElements":450}""", MediaType.APPLICATION_JSON))
        }
        val observations = client.pullOptical()
        assertEquals(450, observations.sumOf { it.rows.size })
        assertTrue(observations.all { it.observedAt == Instant.parse("2026-09-01T10:00:00Z") })
        server.verify()
    }
    @Test
    fun `state observation uses provider time instead of pull time`() {
        val client = HealthOltGatewayHttpClient("http://gateway", "key")
        val field = client.javaClass.getDeclaredField("restTemplate").apply { isAccessible = true }
        val server = MockRestServiceServer.createServer(field.get(client) as RestTemplate)
        server.expect(requestTo("http://gateway/api/olt-gateway/onus/configured?size=200&page=0"))
            .andRespond(withSuccess("""{"items":[{"sn":"SN1","runState":"online","polledAt":"2026-09-01T10:00:00Z"}],"totalPages":1}""", MediaType.APPLICATION_JSON))
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"),client.pullStateObservations().single().observedAt)
        server.verify()
    }

}
