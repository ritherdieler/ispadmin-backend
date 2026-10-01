package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.net.URI
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

class OperationCorrelationTest {

    @AfterEach
    fun clear() = MDC.clear()

    @Test
    fun `filter exposes a valid operation id in the MDC during the request`() {
        val request = MockHttpServletRequest("POST", "/subscription").apply {
            addHeader(CorrelationIdFilter.OPERATION_HEADER, "op-1234567890")
        }
        var seen: String? = null
        val chain = object : MockFilterChain() {
            override fun doFilter(request: ServletRequest, response: ServletResponse) {
                seen = MDC.get(CorrelationIdFilter.OPERATION_MDC_KEY)
            }
        }

        CorrelationIdFilter().doFilter(request, MockHttpServletResponse(), chain)

        assertEquals("op-1234567890", seen)
        assertNull(MDC.get(CorrelationIdFilter.OPERATION_MDC_KEY))
    }

    @Test
    fun `filter ignores malformed operation ids`() {
        val request = MockHttpServletRequest("POST", "/subscription").apply {
            addHeader(CorrelationIdFilter.OPERATION_HEADER, "bad id\nwith newline")
        }
        var seen: String? = "unset"
        val chain = object : MockFilterChain() {
            override fun doFilter(request: ServletRequest, response: ServletResponse) {
                seen = MDC.get(CorrelationIdFilter.OPERATION_MDC_KEY)
            }
        }

        CorrelationIdFilter().doFilter(request, MockHttpServletResponse(), chain)

        assertNull(seen)
    }

    @Test
    fun `outbound interceptor propagates correlation and operation ids to subsystems`() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-1")
        MDC.put(CorrelationIdFilter.OPERATION_MDC_KEY, "op-1234567890")
        val request = MockClientHttpRequest(HttpMethod.POST, URI("http://127.0.0.1/api/olt-gateway/onu/v2/authorize"))
        val execution = ClientHttpRequestExecution { _, _ -> MockClientHttpResponse(ByteArray(0), HttpStatus.OK) }

        OutboundCorrelationInterceptor().intercept(request, ByteArray(0), execution)

        assertEquals("corr-1", request.headers.getFirst(CorrelationIdFilter.HEADER))
        assertEquals("op-1234567890", request.headers.getFirst(CorrelationIdFilter.OPERATION_HEADER))
    }
}
