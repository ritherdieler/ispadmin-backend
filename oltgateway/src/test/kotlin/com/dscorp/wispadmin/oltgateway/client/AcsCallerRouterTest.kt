package com.dscorp.wispadmin.oltgateway.client

import com.dscorp.wispadmin.oltgateway.config.GatewayCallContext
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException

class AcsCallerRouterTest {

    private val properties = OltGatewayProperties().apply {
        acs.requireCaller = true
        acs.internalBaseUrl = "http://core:8080/ispadmin"
        acs.baseUrlProd = "http://core:8080/ispadmin"
        acs.baseUrlStaging = "http://core-staging:8080/ispadmin-staging"
    }
    private val router = AcsCallerRouter(properties)

    @Test
    fun `env stg resuelve la URL de staging`() {
        assertEquals("http://core-staging:8080/ispadmin-staging", router.baseUrl("stg"))
    }

    @Test
    fun `env prod resuelve la URL de prod`() {
        assertEquals("http://core:8080/ispadmin", router.baseUrl("prod"))
    }

    @Test
    fun `el cliente HTTP usa la URL de staging cuando el env es stg`() {
        val http = RestTemplate()
        val server = MockRestServiceServer.createServer(http)
        val client = HttpAcsCpeClient(properties, http, jacksonObjectMapper())
        server.expect(requestTo("http://core-staging:8080/ispadmin-staging/api/acs/v1/cpe/provision"))
            .andRespond(withSuccess("""{"sn":"ZTEGDC47BFFD","status":"PENDING"}""", MediaType.APPLICATION_JSON))
        GatewayCallContext.setEnv("stg")
        try {
            client.provision(AcsCpeProvisionRequest("ZTEGDC47BFFD"))
        } finally {
            GatewayCallContext.clear()
        }
        server.verify()
    }

    @Test
    fun `sin header responde 400`() {
        val error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
            router.baseUrl(null)
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.status)
    }
}
