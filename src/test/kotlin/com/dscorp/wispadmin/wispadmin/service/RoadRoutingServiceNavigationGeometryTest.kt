package com.dscorp.wispadmin.wispadmin.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoadRoutingServiceNavigationGeometryTest {

    @Test
    fun `converts step-relative voice distance to absolute route progress`() {
        assertEquals(
            0.0,
            RoadRoutingService.toAbsoluteDistanceAlongGeometry(
                stepStartDistance = 0.0,
                stepDistance = 98.1,
                remainingAlongStep = 98.1,
            ),
            0.001,
        )
        assertEquals(
            15.0,
            RoadRoutingService.toAbsoluteDistanceAlongGeometry(
                stepStartDistance = 0.0,
                stepDistance = 98.1,
                remainingAlongStep = 83.1,
            ),
            0.001,
        )
        assertEquals(
            149.0,
            RoadRoutingService.toAbsoluteDistanceAlongGeometry(
                stepStartDistance = 98.1,
                stepDistance = 78.6,
                remainingAlongStep = 27.7,
            ),
            0.001,
        )
    }

    @Test
    fun `clamps remaining distance inside the step`() {
        assertEquals(
            10.0,
            RoadRoutingService.toAbsoluteDistanceAlongGeometry(
                stepStartDistance = 10.0,
                stepDistance = 50.0,
                remainingAlongStep = 100.0,
            ),
            0.001,
        )
        assertEquals(
            60.0,
            RoadRoutingService.toAbsoluteDistanceAlongGeometry(
                stepStartDistance = 10.0,
                stepDistance = 50.0,
                remainingAlongStep = -5.0,
            ),
            0.001,
        )
    }
}
