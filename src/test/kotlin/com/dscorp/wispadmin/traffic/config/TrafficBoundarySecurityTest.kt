package com.dscorp.wispadmin.traffic.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class TrafficBoundarySecurityTest {
    @Test
    fun `non traffic routes are not gated by the traffic key`() {
        val filter = TrafficApiKeyFilter(TrafficProperties().apply { apiKey = "test-key" }, ObjectMapper())
        for (path in listOf("/traffic/poll", "/traffic/aggregation/catch-up", "/subscription/1/traffic", "/traffic/network/insights", "/unknown")) {
            for (method in listOf("GET", "POST")) {
                val response = MockHttpServletResponse()
                var called = false
                filter.doFilter(MockHttpServletRequest(method, path), response) { _, _ -> called = true }
                assertTrue(called, path)
            }
        }
    }

    @Test
    fun `canonical API accepts only its service key`() {
        val filter = TrafficApiKeyFilter(TrafficProperties().apply { apiKey = "test-key" }, ObjectMapper())
        for (key in listOf("wrong", "test-key")) {
            val request = MockHttpServletRequest("GET", "/api/traffic/v1/config").apply { addHeader("X-Traffic-Key", key) }
            val response = MockHttpServletResponse()
            var called = false
            filter.doFilter(request, response) { _, _ -> called = true }
            assertEquals(key == "test-key", called)
        }
    }

    @Test
    fun `actuator health is not gated by the traffic key`() {
        val filter = TrafficApiKeyFilter(TrafficProperties().apply { apiKey = "test-key" }, ObjectMapper())
        val response = MockHttpServletResponse()
        var called = false
        filter.doFilter(MockHttpServletRequest("GET", "/actuator/health"), response) { _, _ -> called = true }
        assertTrue(called)
        assertEquals(200, response.status)
    }

    @Test
    fun `filter registration protects only traffic API paths`() {
        val registration = TrafficWebConfig().trafficApiKeyFilterRegistration(TrafficProperties(), ObjectMapper())
        assertEquals(listOf("/api/traffic/*"), registration.urlPatterns.toList())
    }

    @Test
    fun `missing traffic key on canonical API returns 401`() {
        val filter = TrafficApiKeyFilter(TrafficProperties().apply { apiKey = "test-key" }, ObjectMapper())
        val response = MockHttpServletResponse()
        var called = false
        filter.doFilter(MockHttpServletRequest("GET", "/api/traffic/v1/config"), response) { _, _ -> called = true }
        assertFalse(called)
        assertEquals(401, response.status)
    }
}
