package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.dto.SignalPollStatusDto
import com.dscorp.wispadmin.oltgateway.dto.SyncStatusDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executor

class OltGatewaySyncJobRunnerTest {

    private val inventorySyncService = mockk<OltInventorySyncService>(relaxed = true)
    private val signalPollService = mockk<OltSignalPollService>(relaxed = true)
    private val submitted = mutableListOf<Runnable>()
    private val deferredExecutor = Executor { submitted.add(it) }

    private fun runner(executor: Executor = deferredExecutor) =
        OltGatewaySyncJobRunner(inventorySyncService, signalPollService, executor)

    @Test
    fun `arrancar el inventario no ejecuta el trabajo en el hilo que responde`() {
        every { inventorySyncService.status() } returns SyncStatusDto(running = false)

        val status = runner().startInventory()

        assertTrue(status.started)
        assertEquals(OltGatewaySyncJobRunner.JOB_INVENTORY, status.job)
        verify(exactly = 0) { inventorySyncService.syncInventory() }
        assertEquals(1, submitted.size)

        submitted.single().run()
        verify(exactly = 1) { inventorySyncService.syncInventory() }
    }

    @Test
    fun `no encola un segundo inventario si ya hay uno corriendo`() {
        every { inventorySyncService.status() } returns SyncStatusDto(running = true)

        val status = runner().startInventory()

        assertFalse(status.started)
        assertTrue(status.running)
        assertEquals("already_running", status.skippedReason)
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `el inventario por snmp usa su propia ruta`() {
        every { inventorySyncService.status() } returns SyncStatusDto(running = false)

        val status = runner().startSnmpInventory()
        submitted.single().run()

        assertEquals(OltGatewaySyncJobRunner.JOB_SNMP_INVENTORY, status.job)
        verify(exactly = 1) { inventorySyncService.syncInventoryFromSnmp() }
        verify(exactly = 0) { inventorySyncService.syncInventory() }
    }

    @Test
    fun `arrancar el sondeo de senal responde antes de terminar`() {
        every { signalPollService.status() } returns SignalPollStatusDto(running = false)

        val status = runner().startSignal()

        assertTrue(status.started)
        verify(exactly = 0) { signalPollService.pollSignals() }
        submitted.single().run()
        verify(exactly = 1) { signalPollService.pollSignals() }
    }

    @Test
    fun `no encola un segundo sondeo de senal si ya hay uno corriendo`() {
        every { signalPollService.status() } returns SignalPollStatusDto(running = true)

        val status = runner().startSignal()

        assertFalse(status.started)
        assertEquals("already_running", status.skippedReason)
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `un fallo del trabajo no escapa del executor`() {
        every { inventorySyncService.status() } returns SyncStatusDto(running = false)
        every { inventorySyncService.syncInventory() } throws IllegalStateException("olt_unreachable")

        runner { it.run() }.startInventory()

        verify(exactly = 1) { inventorySyncService.syncInventory() }
    }
}
