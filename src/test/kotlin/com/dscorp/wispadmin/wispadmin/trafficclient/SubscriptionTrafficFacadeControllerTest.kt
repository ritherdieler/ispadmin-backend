package com.dscorp.wispadmin.wispadmin.trafficclient

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.http.ResponseEntity
import io.mockk.every
import io.mockk.mockk

class SubscriptionTrafficFacadeControllerTest {

    private val client = mockk<TrafficHttpClient>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(SubscriptionTrafficFacadeController(client)).build()

    @Test
    fun `latest proxies by-subscription canonical API`() {
        every { client.getJson("/api/traffic/v1/by-subscription/2360/latest", null) } returns ResponseEntity.ok(
            """{"subscriptionId":2360,"ip":"192.168.250.20","sampleStatus":"OK"}"""
        )

        mockMvc.get("/subscription/2360/traffic/latest").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.subscriptionId") { value(2360) }
            jsonPath("$.ip") { value("192.168.250.20") }
        }
    }
}
