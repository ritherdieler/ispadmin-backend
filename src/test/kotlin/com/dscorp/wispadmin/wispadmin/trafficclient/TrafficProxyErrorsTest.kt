package com.dscorp.wispadmin.wispadmin.trafficclient

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException

class TrafficProxyErrorsTest {
    @Test
    fun `provider validation missing resource and conflict retain their public meaning`() {
        for (status in listOf(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.TOO_MANY_REQUESTS)) {
            val client = mockk<TrafficHttpClient>()
            every { client.getJson(any(), any()) } throws HttpClientErrorException(status)
            MockMvcBuilders.standaloneSetup(SubscriptionTrafficFacadeController(client)).build()
                .get("/subscription/1/traffic/latest").andExpect { status { isEqualTo(status.value()) } }
        }
    }
}
