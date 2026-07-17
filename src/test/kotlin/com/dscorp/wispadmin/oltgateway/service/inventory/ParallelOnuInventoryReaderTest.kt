package com.dscorp.wispadmin.oltgateway.service.inventory

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltModel
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.FixtureLoader
import com.dscorp.wispadmin.oltgateway.parser.OnuSummaryParser
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ParallelOnuInventoryReaderTest {

    private val cliBus = mockk<OltCliBus>()
    private val session = mockk<HuaweiCliSession>()
    private val oltRepository = mockk<OltMgrOltRepository>()
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        session.poolSize = 1
        inventory.maxSlotProbe = 3
        inventory.defaultPortsPerGponBoard = 2
        inventory.topologyCacheTtlMs = 0
    }

    @Test
    fun `listado masivo usa un solo display ont info 0 all en job INVENTORY serial`() {
        val model = OltMgrOltModel(
            id = 1L,
            code = "MA5608T",
            maxConcurrentCliSessions = 4,
            maxSlotProbe = 3,
            defaultPortsPerGponBoard = 2
        )
        val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2", model = model)
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)

        val commands = ConcurrentHashMap.newKeySet<String>()
        val board0 = FixtureLoader.load("display-board-frame-0-chassis.txt")
        every { session.execute(any()) } answers {
            val cmd = firstArg<String>()
            commands += cmd
            when {
                cmd == "display board 0" -> board0
                cmd.startsWith("display board ") -> "% Parameter error\n"
                cmd == "display ont info 0 all" -> FixtureLoader.load("display-ont-info-0-all-live.txt")
                else -> ""
            }
        }
        every { cliBus.execute(CliJobType.INVENTORY, any<(HuaweiCliSession) -> Any>()) } answers {
            val block = secondArg<(HuaweiCliSession) -> Any>()
            CliBusResult.Ok(block(session))
        }

        val reader = ParallelOnuInventoryReader(
            cliBus = cliBus,
            boardParser = BoardParser(),
            onuSummaryParser = OnuSummaryParser(),
            oltRepository = oltRepository,
            properties = properties
        )

        val result = reader.listOnusParsed()

        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.slot == 0 })
        assertTrue(result.any { it.slot == 1 })
        assertEquals(1, commands.count { it == "display ont info 0 all" })
        assertTrue(commands.none { it.matches(Regex("""display ont info 0 \d+ \d+ all""")) })
        assertTrue(commands.none { it.matches(Regex("""display ont info 0 \d+ all""")) })
        verify(exactly = 1) { session.execute("display ont info 0 all") }
        verify(exactly = 1) { cliBus.execute(CliJobType.INVENTORY, any<(HuaweiCliSession) -> Any>()) }
    }

    @Test
    fun `dedupe por sn entre jobs sin fan-out de workers`() {
        val model = OltMgrOltModel(
            id = 1L,
            code = "MA5608T",
            maxConcurrentCliSessions = 4,
            maxSlotProbe = 1,
            defaultPortsPerGponBoard = 1
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(
            OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "1.1.1.1", model = model)
        )
        val calls = AtomicInteger()
        every { session.execute(any()) } answers {
            val cmd = firstArg<String>()
            when {
                cmd == "display board 0" -> FixtureLoader.load("display-board-frame-0-chassis.txt")
                cmd.startsWith("display board ") -> "% Parameter error\n"
                else -> {
                    calls.incrementAndGet()
                    FixtureLoader.load("display-ont-info-0-all-live.txt")
                }
            }
        }
        every { cliBus.execute(CliJobType.INVENTORY, any<(HuaweiCliSession) -> Any>()) } answers {
            val block = secondArg<(HuaweiCliSession) -> Any>()
            CliBusResult.Ok(block(session))
        }

        val reader = ParallelOnuInventoryReader(
            cliBus = cliBus,
            boardParser = BoardParser(),
            onuSummaryParser = OnuSummaryParser(),
            oltRepository = oltRepository,
            properties = properties
        )

        val result = reader.listOnusParsed()
        assertEquals(result.map { it.sn.uppercase() }.toSet().size, result.size)
        assertEquals(1, calls.get())
    }
}
