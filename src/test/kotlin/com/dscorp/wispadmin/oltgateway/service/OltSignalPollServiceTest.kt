package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltModel
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedBoard
import com.dscorp.wispadmin.oltgateway.parser.ParsedOpticalInfo
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

class OltSignalPollServiceTest {

    private val cliBus = mockk<OltCliBus>()
    private val oltRepository = mockk<OltMgrOltRepository>()
    private val onuRepository = mockk<OltMgrOnuRepository>()
    private val statusRepository = mockk<OltMgrOnuStatusCurrentRepository>()
    private val taskRepository = mockk<OltMgrTaskRepository>()
    private val boardParser = mockk<BoardParser>()
    private val opticalInfoParser = mockk<OpticalInfoParser>()
    private val signalCategoryCalculator = SignalCategoryCalculator()
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        sync.skipWhenWriteRunning = true
        inventory.maxSlotProbe = 1
        inventory.defaultPortsPerGponBoard = 2
    }

    private lateinit var service: OltSignalPollService
    private val model = OltMgrOltModel(
        id = 1L,
        code = "MA5608T",
        vendor = "Huawei",
        product = "MA5608T",
        family = "MA5600T",
        maxConcurrentCliSessions = 4,
        maxSlotProbe = 1,
        defaultPortsPerGponBoard = 2
    )
    private val olt = OltMgrOlt(
        id = 1L,
        name = "gigafiber-ma5608t",
        ipAddress = "10.11.104.2",
        model = model
    )

    @BeforeEach
    fun setUp() {
        service = OltSignalPollService(
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            taskRepository = taskRepository,
            boardParser = boardParser,
            opticalInfoParser = opticalInfoParser,
            signalCategoryCalculator = signalCategoryCalculator,
            properties = properties,
            cliBus = cliBus
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { taskRepository.existsByStatus("running") } returns false
        every { statusRepository.save(any()) } answers { firstArg() }
        every { cliBus.queueDepth() } returns 0
        every { cliBus.busyJobType() } returns null
    }

    @Test
    fun `un solo job SIGNAL_POLL en el bus con slots y puertos secuenciales`() {
        val session = mockk<HuaweiCliSession>()
        val commands = mutableListOf<String>()
        every { session.execute(any()) } answers {
            val cmd = firstArg<String>()
            commands += cmd
            when {
                cmd.startsWith("display board") -> "board-output"
                cmd.startsWith("display ont optical-info") -> "optical-output"
                else -> "ok"
            }
        }
        every { boardParser.parseAll("board-output") } returns listOf(
            ParsedBoard(slot = 0, boardName = "H801GPHF", status = "Normal"),
            ParsedBoard(slot = 1, boardName = "H801GPHF", status = "Normal")
        )
        every { opticalInfoParser.parseAll("optical-output") } returns emptyList()
        every { onuRepository.findByOlt_Id(1L) } returns emptyList()
        stubSignalPollExecute(session)

        val result = service.pollSignals()

        verify(exactly = 1) { cliBus.execute(CliJobType.SIGNAL_POLL, any<(HuaweiCliSession) -> Any>()) }
        assertEquals(
            listOf(
                "display board 0",
                "display board 1",
                "interface gpon 0/0",
                "display ont optical-info 0 all",
                "display ont optical-info 1 all",
                "quit",
                "interface gpon 0/1",
                "display ont optical-info 0 all",
                "display ont optical-info 1 all",
                "quit"
            ),
            commands
        )
        assertEquals(2, result.slotsPolled)
        assertEquals(4, result.portsPolled)
        assertEquals(0, result.onusUpdated)
        assertNull(result.skippedReason)
        assertNull(result.error)
        assertTrue(result.durationMs >= 0)
    }

    @Test
    fun `upsert optical fields sin pisar run_state ni match_state`() {
        val session = mockk<HuaweiCliSession>()
        every { session.execute(any()) } answers {
            val cmd = firstArg<String>()
            when {
                cmd.startsWith("display board") -> "board-output"
                cmd == "display ont optical-info 0 all" -> "opt-0"
                else -> "ok"
            }
        }
        every { boardParser.parseAll("board-output") } returns listOf(
            ParsedBoard(slot = 0, boardName = "H801GPHF", status = "Normal")
        )
        every { opticalInfoParser.parseAll("opt-0") } returns listOf(
            ParsedOpticalInfo(
                ontId = 1,
                rxPowerDbm = -18.54,
                txPowerDbm = 2.20,
                oltRxPowerDbm = -24.82,
                temperatureC = 57.0
            )
        )
        every { opticalInfoParser.parseAll("ok") } returns emptyList()

        val onu = OltMgrOnu(
            id = 10L,
            sn = "SNOPT001",
            externalId = "gigafiber-ma5608t_0_0_1",
            olt = olt,
            board = 0,
            port = 0,
            onuIndex = 1
        )
        val status = OltMgrOnuStatusCurrent(
            onuId = 10L,
            onu = onu,
            runState = "online",
            matchState = "match"
        )
        onu.status = status
        every { onuRepository.findByOlt_Id(1L) } returns listOf(onu)
        every { statusRepository.findById(10L) } returns Optional.of(status)
        stubSignalPollExecute(session)

        val result = service.pollSignals()

        assertEquals(1, result.onusUpdated)
        assertEquals("online", status.runState)
        assertEquals("match", status.matchState)
        assertEquals(0, BigDecimal("-18.54").compareTo(status.onuRxDbm))
        assertEquals(0, BigDecimal("2.20").compareTo(status.onuTxDbm))
        assertEquals(0, BigDecimal("-24.82").compareTo(status.oltRxDbm))
        assertEquals(57, status.temperatureC)
        assertEquals("good", status.signalCategory)
        assertTrue(status.polledAt != null)
        verify { statusRepository.save(status) }
    }

    @Test
    fun `skip cuando write task running`() {
        every { taskRepository.existsByStatus("running") } returns true

        val result = service.pollSignals()

        assertEquals("write_task_running", result.skippedReason)
        verify(exactly = 0) { cliBus.execute(any(), any<(HuaweiCliSession) -> Any>()) }
    }

    @Test
    fun `skip cuando el bus rechaza SIGNAL_POLL duplicado`() {
        every {
            cliBus.execute(CliJobType.SIGNAL_POLL, any<(HuaweiCliSession) -> Any>())
        } returns CliBusResult.Skipped("already_running")

        val result = service.pollSignals()

        assertEquals("already_running", result.skippedReason)
        assertFalse(service.isRunning())
    }

    @Test
    fun `skip segundo poll concurrente`() {
        val session = mockk<HuaweiCliSession>()
        every { session.execute(any()) } returns "ok"
        every { boardParser.parseAll(any()) } returns emptyList()
        every { onuRepository.findByOlt_Id(1L) } returns emptyList()
        val entered = AtomicInteger(0)
        every { cliBus.execute(CliJobType.SIGNAL_POLL, any<(HuaweiCliSession) -> Any>()) } answers {
            entered.incrementAndGet()
            val parallel = service.pollSignals()
            assertEquals("sync_already_running", parallel.skippedReason)
            val block = secondArg<(HuaweiCliSession) -> Any>()
            CliBusResult.Ok(block(session))
        }

        val result = service.pollSignals()

        assertNull(result.skippedReason)
        assertEquals(1, entered.get())
        assertFalse(service.isRunning())
    }

    @Test
    fun `status incluye lastResult y bus`() {
        every { cliBus.queueDepth() } returns 2
        every { cliBus.busyJobType() } returns CliJobType.SIGNAL_POLL
        every {
            cliBus.execute(CliJobType.SIGNAL_POLL, any<(HuaweiCliSession) -> Any>())
        } returns CliBusResult.Skipped("already_queued")

        service.pollSignals()
        val status = service.status()

        assertFalse(status.running)
        assertEquals("already_queued", status.lastResult?.skippedReason)
        assertEquals(2, status.busQueueDepth)
        assertEquals("SIGNAL_POLL", status.busBusyJobType)
    }

    private fun stubSignalPollExecute(session: HuaweiCliSession) {
        every { cliBus.execute(CliJobType.SIGNAL_POLL, any<(HuaweiCliSession) -> Any>()) } answers {
            val block = secondArg<(HuaweiCliSession) -> Any>()
            CliBusResult.Ok(block(session))
        }
    }
}
