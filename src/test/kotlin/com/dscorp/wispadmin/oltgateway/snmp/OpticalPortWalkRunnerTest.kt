package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class OpticalPortWalkRunnerTest {

    @Test
    fun `un puerto fallido no aborta los demas`() {
        val ports = listOf(GponFsp(0, 0, 0), GponFsp(0, 0, 1), GponFsp(0, 0, 2))
        val calls = AtomicInteger(0)
        val rows = OpticalPortWalkRunner.runAll(ports, parallelism = 1) { port ->
            calls.incrementAndGet()
            if (port.port == 1) throw IOException("timeout")
            listOf("ok-${port.port}")
        }
        assertEquals(2, rows.size)
        assertEquals(3, calls.get())
        assertTrue(rows.contains("ok-0"))
        assertTrue(rows.contains("ok-2"))
    }

    @Test
    fun `si todos fallan lanza IOException`() {
        val ports = listOf(GponFsp(0, 0, 0), GponFsp(0, 0, 1))
        assertThrows(IOException::class.java) {
            OpticalPortWalkRunner.runAll<String>(ports, parallelism = 2) {
                throw IOException("timeout")
            }
        }
    }
}
