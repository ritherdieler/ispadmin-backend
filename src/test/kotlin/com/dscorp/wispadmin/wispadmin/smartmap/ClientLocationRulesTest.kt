package com.dscorp.wispadmin.wispadmin.smartmap

import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientLocationRulesTest {

    @Test
    fun `hasRealClientGps returns false for default coordinate`() {
        val location = GeoLocation(ClientLocationRules.DEFAULT_LAT, ClientLocationRules.DEFAULT_LNG)
        assertFalse(ClientLocationRules.hasRealClientGps(location))
    }

    @Test
    fun `hasRealClientGps returns false for zero coordinate`() {
        assertFalse(ClientLocationRules.hasRealClientGps(GeoLocation(0.0, 0.0)))
    }

    @Test
    fun `hasRealClientGps returns false for null location`() {
        assertFalse(ClientLocationRules.hasRealClientGps(null))
    }

    @Test
    fun `hasRealClientGps returns true for real nearby coordinate`() {
        val location = GeoLocation(-11.235528, -77.377746)
        assertTrue(ClientLocationRules.hasRealClientGps(location))
    }

    @Test
    fun `isDefaultClientCoordinate matches rounded default values`() {
        assertTrue(ClientLocationRules.isDefaultClientCoordinate(-11.2337080001, -77.3762780001))
    }

    @Test
    fun `isDefaultClientCoordinate returns false for distinct coordinate`() {
        assertFalse(ClientLocationRules.isDefaultClientCoordinate(-11.234636, -77.378812))
    }
}
