package com.dscorp.wispadmin.acs.genieacs

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class Tr069ModelProfileRegistryTest {

    @Test
    fun `empty catalog leaves no imported profiles`() {
        val registry = Tr069ModelProfileRegistry(mockk(relaxed = true), ObjectMapper(), "")
        registry.init()
        assertFalse(registry.hasImportedProfiles())
    }

    @Test
    fun `resolves F6600R aliases through dynamic resolver`() {
        val registry = Tr069ModelProfileRegistry(mockk(relaxed = true), ObjectMapper(), "")
        val profile = Tr069ModelProfile(
            productClass = "F6600R",
            wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
            wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
        )
        registry.replaceProfilesForTests(
            mapOf(
                "F6600R" to profile,
                "F6600" to profile,
                "F6600RV9.0.21" to profile,
            )
        )
        assertTrue(registry.hasImportedProfiles())
        assertNotNull(registry.resolve("F6600RV9.0.21", null))
        assertEquals("F6600R", Tr069ModelProfiles.resolve(null, "F6600R")?.productClass)
    }
}
