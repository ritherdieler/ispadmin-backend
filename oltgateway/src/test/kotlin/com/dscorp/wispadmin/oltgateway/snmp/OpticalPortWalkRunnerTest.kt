package com.dscorp.wispadmin.oltgateway.snmp

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.dscorp.wispadmin.oltgateway.ssh.LocalCliBusPressure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class OpticalPortWalkRunnerTest {

    @Test
    fun `un puerto fallido no aborta los demas`() {
        val ports = listOf(GponFsp(0, 0, 0), GponFsp(0, 0, 1), GponFsp(0, 0, 2))
        val calls = AtomicInteger(0)
        val batch = OpticalPortWalkRunner.runAll(ports, parallelism = 1) { port ->
            calls.incrementAndGet()
            if (port.port == 1) throw IOException("timeout")
            listOf("ok-${port.port}")
        }
        assertEquals(4, calls.get())
        assertEquals(2, batch.items.size)
        assertEquals(3, batch.portsAttempted)
        assertEquals(1, batch.portsFailed)
        assertTrue(batch.items.contains("ok-0"))
        assertTrue(batch.items.contains("ok-2"))
    }

    @Test
    fun `puerto fallido se reintenta una vez al final del ciclo`() {
        val ports = listOf(GponFsp(0, 0, 0), GponFsp(0, 0, 1), GponFsp(0, 0, 2))
        val calls = mutableListOf<Int>()
        val batch = OpticalPortWalkRunner.runAll(ports, parallelism = 1) { port ->
            calls.add(port.port)
            if (port.port == 1 && calls.count { it == 1 } == 1) {
                throw IOException("timeout")
            }
            listOf("ok-${port.port}")
        }
        assertEquals(listOf(0, 1, 2, 1), calls)
        assertEquals(3, batch.items.size)
        assertEquals(3, batch.portsAttempted)
        assertEquals(0, batch.portsFailed)
        assertTrue(batch.items.contains("ok-1"))
    }

    @Test
    fun `retry fallido sigue contando el puerto como failed`() {
        val ports = listOf(GponFsp(0, 0, 0), GponFsp(0, 0, 1))
        val calls = AtomicInteger(0)
        val batch = OpticalPortWalkRunner.runAll(ports, parallelism = 1) { port ->
            calls.incrementAndGet()
            if (port.port == 1) throw IOException("timeout")
            listOf("ok-${port.port}")
        }
        assertEquals(3, calls.get())
        assertEquals(1, batch.items.size)
        assertEquals(1, batch.portsFailed)
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

    @Test
    fun `PORT_FAIL incluye metrica local del CLI bus`() {
        val ports = listOf(GponFsp(0, 1, 6))
        val messages = captureLogs(OpticalPortWalkRunner::class.java.name) {
            assertThrows(IOException::class.java) {
                OpticalPortWalkRunner.runAll<String>(
                    ports = ports,
                    parallelism = 1,
                    pressureSnapshot = {
                        LocalCliBusPressure(localQueueDepth = 3, localBusyJobType = "INVENTORY")
                    }
                ) {
                    throw IOException("timeout")
                }
            }
        }
        val fail = messages.filter { it.contains("SNMP_OPTICAL_PORT_FAIL") }
        assertTrue(fail.isNotEmpty())
        assertTrue(fail.all { it.contains("slot=1") })
        assertTrue(fail.all { it.contains("port=6") })
        assertTrue(fail.all { it.contains("localCliBus=true") })
        assertTrue(fail.all { it.contains("localQueueDepth=3") })
        assertTrue(fail.all { it.contains("localBusyJobType=INVENTORY") })
        assertTrue(fail.all { it.contains("sshActive=n/a") })
        assertTrue(fail.all { it.contains("sshMax=n/a") })
    }

    private fun captureLogs(loggerName: String, block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(loggerName) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            block()
            return appender.list.map { it.formattedMessage }
        } finally {
            logger.detachAppender(appender)
        }
    }
}
