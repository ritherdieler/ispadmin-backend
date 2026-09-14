package com.dscorp.wispadmin.wispadmin.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class EnvironmentServiceTest {

    @Test
    fun `isDevelopment is true when active profile is exactly dev`() {
        val service = EnvironmentService()
        ReflectionTestUtils.setField(service, "activeProfile", "dev")

        assertTrue(service.isDevelopment())
    }

    @Test
    fun `isDevelopment is true when dev is one of several active profiles`() {
        val service = EnvironmentService()
        ReflectionTestUtils.setField(service, "activeProfile", "dev,local")

        assertTrue(service.isDevelopment())
    }

    @Test
    fun `isDevelopment is false for production profile`() {
        val service = EnvironmentService()
        ReflectionTestUtils.setField(service, "activeProfile", "prod")

        assertFalse(service.isDevelopment())
    }
}
