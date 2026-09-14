package com.dscorp.wispadmin.oltgateway.service.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InventoryJobPlannerTest {

    @Test
    fun `when gpon slots exist plans slot all job per slot`() {
        val slots = listOf(
            GponSlotInfo(0, "H805GPFD", 16),
            GponSlotInfo(1, "H806GPFD", 16)
        )

        val jobs = InventoryJobPlanner.plan(slots, maxSessions = 4)

        assertEquals(2, jobs.size)
        assertEquals(InventoryCliJob.SlotAll(0), jobs[0])
        assertEquals(InventoryCliJob.SlotAll(1), jobs[1])
        assertEquals("display ont info 0 0 all", jobs[0].command())
        assertEquals("display ont info 0 1 all", jobs[1].command())
    }

    @Test
    fun `when many slots plans one slot all job per gpon slot`() {
        val slots = (0 until 6).map { GponSlotInfo(it, "H805GPFD", 16) }

        val jobs = InventoryJobPlanner.plan(slots, maxSessions = 4)

        assertEquals(6, jobs.size)
        assertTrue(jobs.all { it is InventoryCliJob.SlotAll })
        assertEquals((0 until 6).map { "display ont info 0 $it all" }, jobs.map { it.command() })
    }

    @Test
    fun `empty slots yields empty jobs`() {
        assertTrue(InventoryJobPlanner.plan(emptyList(), 4).isEmpty())
    }
}
