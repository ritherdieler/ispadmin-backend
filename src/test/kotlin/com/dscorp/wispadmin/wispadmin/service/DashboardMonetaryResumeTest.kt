package com.dscorp.wispadmin.wispadmin.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardMonetaryResumeTest {

    @Test
    fun `collection components equal gross revenue when cohort is consistent`() {
        val grossRevenue = 49_877.0
        val totalRaised = 10_849.0
        val totalDiscount = 561.0
        val totalToCollect = 38_467.0

        assertEquals(
            grossRevenue,
            totalRaised + totalDiscount + totalToCollect,
            0.01
        )
    }

    @Test
    fun `misaligned calendar cash flow does not match gross revenue`() {
        val grossRevenue = 49_877.0
        val totalRaised = 11_922.0
        val totalDiscount = 538.0
        val totalToCollect = 38_467.0

        assertTrue(
            kotlin.math.abs(grossRevenue - (totalRaised + totalDiscount + totalToCollect)) > 0.01
        )
    }
}
