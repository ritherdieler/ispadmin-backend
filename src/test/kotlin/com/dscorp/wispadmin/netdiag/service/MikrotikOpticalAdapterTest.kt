package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MikrotikOpticalAdapterTest {

    private val adapter = MikrotikOpticalAdapter()

    @Test
    fun `collect usa ethernet monitor once por interfaz troncal`() {
        val session = mockk<MikrotikSession>()
        every {
            session.call(
                "/interface/ethernet/monitor",
                mapOf("numbers" to "sfp-sfpplus1", "once" to "")
            )
        } returns listOf(
            mapOf(
                "name" to "sfp-sfpplus1",
                "sfp-rx-power" to "-12.5",
                "sfp-tx-power" to "1.2",
                "sfp-temperature" to "41",
                "sfp-module-present" to "true"
            )
        )

        val rows = adapter.collect(
            session,
            TargetMonitorConfig(opticalInterfaces = listOf("sfp-sfpplus1"))
        )

        assertEquals(1, rows.size)
        assertEquals("sfp-sfpplus1", rows[0].interfaceName)
        assertEquals(-12.5, rows[0].rxPowerDbm)
        assertEquals(1.2, rows[0].txPowerDbm)
        assertEquals(41.0, rows[0].temperatureC)
        assertEquals(true, rows[0].sfpPresent)
    }

    @Test
    fun `collect sin opticalInterfaces no llama al router`() {
        val session = mockk<MikrotikSession>(relaxed = true)

        val rows = adapter.collect(session, TargetMonitorConfig())

        assertTrue(rows.isEmpty())
        verify(exactly = 0) { session.call(any(), any()) }
    }

    @Test
    fun `collect tolera monitor fallido por interfaz`() {
        val session = mockk<MikrotikSession>()
        every {
            session.call("/interface/ethernet/monitor", mapOf("numbers" to "sfp-sfpplus1", "once" to ""))
        } throws RuntimeException("no sfp")
        every {
            session.call("/interface/ethernet/monitor", mapOf("numbers" to "sfp-sfpplus2", "once" to ""))
        } returns listOf(
            mapOf("name" to "sfp-sfpplus2", "sfp-rx-power" to "-9.0", "sfp-tx-power" to "0.5")
        )

        val rows = adapter.collect(
            session,
            TargetMonitorConfig(opticalInterfaces = listOf("sfp-sfpplus1", "sfp-sfpplus2"))
        )

        assertEquals(1, rows.size)
        assertEquals("sfp-sfpplus2", rows[0].interfaceName)
        assertNull(rows.find { it.interfaceName == "sfp-sfpplus1" })
    }
}
