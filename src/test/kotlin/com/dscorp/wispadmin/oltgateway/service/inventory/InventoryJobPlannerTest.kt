package com.dscorp.wispadmin.oltgateway.service.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InventoryJobPlannerTest {

    @Test
    fun `when gpon slots exist plans single frame all job`() {
        val slots = listOf(
            GponSlotInfo(0, "H805GPFD", 16),
            GponSlotInfo(1, "H806GPFD", 16)
        )

        val jobs = InventoryJobPlanner.plan(slots, maxSessions = 4)

        assertEquals(1, jobs.size)
        assertTrue(jobs.single() is InventoryCliJob.FrameAll)
        assertEquals("display ont info 0 all", jobs.single().command())
    }

    @Test
    fun `when many slots still plans single frame all job`() {
        val slots = (0 until 6).map { GponSlotInfo(it, "H805GPFD", 16) }

        val jobs = InventoryJobPlanner.plan(slots, maxSessions = 4)

        assertEquals(1, jobs.size)
        assertEquals("display ont info 0 all", jobs.single().command())
    }

    @Test
    fun `empty slots yields empty jobs`() {
        assertTrue(InventoryJobPlanner.plan(emptyList(), 4).isEmpty())
    }
}
