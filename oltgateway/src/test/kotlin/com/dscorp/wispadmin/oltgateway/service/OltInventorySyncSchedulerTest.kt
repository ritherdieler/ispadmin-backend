package com.dscorp.wispadmin.oltgateway.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class OltInventorySyncSchedulerTest {

    private val syncService = mockk<OltInventorySyncService>()
    private val scheduler = OltInventorySyncScheduler(syncService)

    @Test
    fun `scheduledInventorySync delega a syncInventory`() {
        every { syncService.syncInventory() } returns SyncResult(inserted = 1, durationMs = 10)

        scheduler.scheduledInventorySync()

        verify(exactly = 1) { syncService.syncInventory() }
    }

    @Test
    fun `scheduledInventorySync no falla cuando skip`() {
        every { syncService.syncInventory() } returns SyncResult(skippedReason = "write_task_running")

        scheduler.scheduledInventorySync()

        verify(exactly = 1) { syncService.syncInventory() }
    }
}
