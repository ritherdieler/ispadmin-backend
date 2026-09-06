package com.dscorp.wispadmin.wispadmin.trafficclient
import com.dscorp.wispadmin.wispadmin.util.SubsystemProxyAdvice
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.net.SocketTimeoutException
class ProxyErrorEnvelopeTest {
    @Test fun `public error identifies source and hides provider details`() {
        val client=mockk<TrafficHttpClient>()
        every { client.getJson(any(),any()) } throws HttpClientErrorException(HttpStatus.UNAUTHORIZED,"secret-upstream-details")
        val mvc=MockMvcBuilders.standaloneSetup(SubscriptionTrafficFacadeController(client)).setControllerAdvice(SubsystemProxyAdvice()).build()
        mvc.get("/subscription/7/traffic/latest").andExpect {
            status { isBadGateway() };jsonPath("$.source") { value("traffic") };jsonPath("$.code") { value("UPSTREAM_AUTHENTICATION_FAILED") }
            jsonPath("$.retryable") { value(false) };jsonPath("$.correlationId") { isNotEmpty() }
        }
    }
    @Test fun `socket timeout is gateway timeout rather than unavailable`() {
        val client=mockk<TrafficHttpClient>()
        every { client.getJson(any(),any()) } throws ResourceAccessException("timeout",SocketTimeoutException())
        val mvc=MockMvcBuilders.standaloneSetup(SubscriptionTrafficFacadeController(client)).setControllerAdvice(SubsystemProxyAdvice()).build()
        mvc.get("/subscription/7/traffic/latest").andExpect { status { isGatewayTimeout() };jsonPath("$.code") { value("UPSTREAM_TIMEOUT") } }
    }
}
