package com.dscorp.wispadmin.wispadmin.smartmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException

class CollectionTravelModeRoutingTest {

    @Test
    fun `resolves vehicle profile from configuration`() {
        assertEquals(
            "mapbox/driving-traffic",
            CollectionTravelModeRouting.resolveMapboxProfile(
                CollectionTravelMode.VEHICLE,
                "mapbox/driving-traffic",
            ),
        )
    }

    @Test
    fun `resolves walking profile`() {
        assertEquals(
            "mapbox/walking",
            CollectionTravelModeRouting.resolveMapboxProfile(
                CollectionTravelMode.WALKING,
                "mapbox/driving-traffic",
            ),
        )
    }

    @Test
    fun `uses congestion annotations only for vehicle`() {
        assertEquals(
            "duration,congestion",
            CollectionTravelModeRouting.resolveDirectionsAnnotations(
                CollectionTravelMode.VEHICLE,
                "duration,congestion",
            ),
        )
        assertEquals(
            "duration",
            CollectionTravelModeRouting.resolveDirectionsAnnotations(
                CollectionTravelMode.WALKING,
                "duration,congestion",
            ),
        )
    }

    @Test
    fun `curb approaches only apply to vehicle`() {
        assertTrue(CollectionTravelModeRouting.shouldUseCurbApproaches(CollectionTravelMode.VEHICLE))
        assertFalse(CollectionTravelModeRouting.shouldUseCurbApproaches(CollectionTravelMode.WALKING))
    }

    @Test
    fun `avoid maneuver radius only applies to vehicle in motion`() {
        assertEquals(
            75,
            CollectionTravelModeRouting.resolveAvoidManeuverRadiusMeters(
                CollectionTravelMode.VEHICLE,
                requestedRadius = null,
                configuredDefaultRadius = 75,
                inMotion = true,
            ),
        )
        assertNull(
            CollectionTravelModeRouting.resolveAvoidManeuverRadiusMeters(
                CollectionTravelMode.WALKING,
                requestedRadius = 40,
                configuredDefaultRadius = 75,
                inMotion = true,
            ),
        )
    }

    @Test
    fun `parses travel mode aliases`() {
        assertEquals(CollectionTravelMode.VEHICLE, CollectionTravelMode.fromApiValue("vehicle"))
        assertEquals(CollectionTravelMode.WALKING, CollectionTravelMode.fromApiValue("walking"))
        assertEquals(CollectionTravelMode.WALKING, CollectionTravelMode.fromApiValue("on-foot"))
    }

    @Test
    fun `rejects invalid travel mode`() {
        assertThrows<ResponseStatusException> {
            CollectionTravelMode.fromApiValue("bike")
        }
    }
}
