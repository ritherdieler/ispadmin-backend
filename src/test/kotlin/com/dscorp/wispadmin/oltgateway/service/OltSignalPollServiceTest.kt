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
import com.dscorp.wispadmin.oltgateway.parser.FixtureLoader
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedBoard
import com.dscorp.wispadmin.oltgateway.parser.ParsedOpticalInfo
import com.dscorp.wispadmin.oltgateway.exception.OltCommandTimeoutException
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import com.dscorp.wispadmin.oltgateway.snmp.GponFsp
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpClient
import com.dscorp.wispadmin.oltgateway.snmp.SnmpOntKey
import com.dscorp.wispadmin.oltgateway.snmp.SnmpOntOptical
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
    private val snmpClient = mockk<OltSnmpClient>()
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
        // Legacy tests exercise deprecated SSH optical path
        snmp.enabled = false
        snmp.allowSshSignalFallback = true
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
        properties.snmp.enabled = false
        properties.snmp.allowSshSignalFallback = true
        properties.snmp.roCommunity = ""
        service = OltSignalPollService(
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            taskRepository = taskRepository,
            boardParser = boardParser,
            opticalInfoParser = opticalInfoParser,
            signalCategoryCalculator = signalCategoryCalculator,
            properties = properties,
            cliBus = cliBus,
            snmpClient = snmpClient
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { taskRepository.existsByStatus("running") } returns false
        every { statusRepository.save(any()) } answers { firstArg() }
        every { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) } answers {
            firstArg<Iterable<OltMgrOnuStatusCurrent>>().toList()
        }
        every { cliBus.queueDepth() } returns 0
        every { cliBus.busyJobType() } returns null
    }

    @Test
    fun `optical sin cambios no persiste status`() {
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
            matchState = "match",
            onuRxDbm = BigDecimal("-18.54"),
            onuTxDbm = BigDecimal("2.20"),
            oltRxDbm = BigDecimal("-24.82"),
            temperatureC = 57,
            signalCategory = "good"
        )
        onu.status = status
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu)

        val updated = service.applyOpticalUpdates(
            oltId = 1L,
            rows = listOf(
                OltSignalPollService.OpticalRow(
                    slot = 0,
                    port = 0,
                    optical = ParsedOpticalInfo(
                        ontId = 1,
                        rxPowerDbm = -18.54,
                        txPowerDbm = 2.20,
                        oltRxPowerDbm = -24.82,
                        temperatureC = 57.0
                    )
                )
            )
        )

        assertEquals(0, updated)
        verify(exactly = 0) { statusRepository.save(any()) }
        verify(exactly = 0) { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) }
        verify(exactly = 0) { statusRepository.findById(any()) }
    }

    @Test
    fun `optical cambiado persiste en batch sin findById`() {
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
            matchState = "match",
            onuRxDbm = BigDecimal("-18.54"),
            signalCategory = "good"
        )
        onu.status = status
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu)

        val updated = service.applyOpticalUpdates(
            oltId = 1L,
            rows = listOf(
                OltSignalPollService.OpticalRow(
                    slot = 0,
                    port = 0,
                    optical = ParsedOpticalInfo(
                        ontId = 1,
                        rxPowerDbm = -20.00,
                        txPowerDbm = 2.20,
                        oltRxPowerDbm = -24.82,
                        temperatureC = 57.0
                    )
                )
            )
        )

        assertEquals(1, updated)
        assertEquals(0, BigDecimal("-20.00").compareTo(status.onuRxDbm))
        verify(exactly = 0) { statusRepository.findById(any()) }
        verify { statusRepository.saveAll(match<Iterable<OltMgrOnuStatusCurrent>> { it.single() === status }) }
    }

    @Test
    fun `optical null no pisa oltRx previo cuando columna SNMP falla`() {
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
            matchState = "match",
            onuRxDbm = BigDecimal("-18.54"),
            onuTxDbm = BigDecimal("2.20"),
            oltRxDbm = BigDecimal("-24.82"),
            temperatureC = 57,
            signalCategory = "good"
        )
        onu.status = status
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu)

        val updated = service.applyOpticalUpdates(
            oltId = 1L,
            rows = listOf(
                OltSignalPollService.OpticalRow(
                    slot = 0,
                    port = 0,
                    optical = ParsedOpticalInfo(
                        ontId = 1,
                        rxPowerDbm = -19.00,
                        txPowerDbm = 2.20,
                        oltRxPowerDbm = null,
                        temperatureC = null
                    )
                )
            )
        )

        assertEquals(1, updated)
        assertEquals(0, BigDecimal("-19.00").compareTo(status.onuRxDbm))
        assertEquals(0, BigDecimal("2.20").compareTo(status.onuTxDbm))
        assertEquals(0, BigDecimal("-24.82").compareTo(status.oltRxDbm))
        assertEquals(57, status.temperatureC)
        assertEquals("good", status.signalCategory)
    }

    @Test
    fun `optical todo null no persiste ni borra potencias previas`() {
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
            onuRxDbm = BigDecimal("-18.54"),
            onuTxDbm = BigDecimal("2.20"),
            oltRxDbm = BigDecimal("-24.82"),
            temperatureC = 57,
            signalCategory = "good"
        )
        onu.status = status
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu)

        val updated = service.applyOpticalUpdates(
            oltId = 1L,
            rows = listOf(
                OltSignalPollService.OpticalRow(
                    slot = 0,
                    port = 0,
                    optical = ParsedOpticalInfo(
                        ontId = 1,
                        rxPowerDbm = null,
                        txPowerDbm = null,
                        oltRxPowerDbm = null,
                        temperatureC = null
                    )
                )
            )
        )

        assertEquals(0, updated)
        assertEquals(0, BigDecimal("-24.82").compareTo(status.oltRxDbm))
        verify(exactly = 0) { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) }
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
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns emptyList()
        stubSignalPollExecute(session)

        val result = service.pollSignals()

        verify(exactly = 1) { cliBus.execute(CliJobType.SIGNAL_POLL, any<(HuaweiCliSession) -> Any>()) }
        assertEquals(
            listOf(
                "display board 0",
                "display board 1",
                "interface gpon 0/0"
            ) + (0 until 16).flatMap { port ->
                listOf(
                    "display ont optical-info $port all",
                    "interface gpon 0/0",
                    "display ont optical-info $port all"
                )
            } + listOf(
                "quit",
                "interface gpon 0/1"
            ) + (0 until 16).flatMap { port ->
                listOf(
                    "display ont optical-info $port all",
                    "interface gpon 0/1",
                    "display ont optical-info $port all"
                )
            } + listOf(
                "quit"
            ),
            commands
        )
        assertEquals(2, result.slotsPolled)
        assertEquals(32, result.portsPolled)
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
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu)
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
        verify { statusRepository.saveAll(match<Iterable<OltMgrOnuStatusCurrent>> { it.single() === status }) }
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
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns emptyList()
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
    fun `signal poll parsea fixture live y actualiza ONUs por board port ontId`() {
        val session = mockk<HuaweiCliSession>()
        val liveOutput = FixtureLoader.load("display-ont-optical-info-all-live.txt")
        val realParser = OpticalInfoParser()
        every { session.execute(any()) } answers {
            val cmd = firstArg<String>()
            when {
                cmd.startsWith("display board") -> "board-output"
                cmd == "display ont optical-info 7 all" -> liveOutput
                else -> "ok"
            }
        }
        every { boardParser.parseAll("board-output") } returns listOf(
            ParsedBoard(slot = 1, boardName = "H801GPHF", status = "Normal")
        )
        every { opticalInfoParser.parseAll(liveOutput) } answers { realParser.parseAll(liveOutput) }
        every { opticalInfoParser.parseAll("ok") } returns emptyList()

        val onus = listOf(
            OltMgrOnu(
                id = 10L,
                sn = "SN001",
                externalId = "gigafiber-ma5608t_1_7_1",
                olt = olt,
                board = 1,
                port = 7,
                onuIndex = 1
            ),
            OltMgrOnu(
                id = 11L,
                sn = "SN002",
                externalId = "gigafiber-ma5608t_1_7_2",
                olt = olt,
                board = 1,
                port = 7,
                onuIndex = 2
            )
        )
        val statuses = onus.associate { onu ->
            onu.id!! to OltMgrOnuStatusCurrent(
                onuId = onu.id,
                onu = onu,
                runState = "online",
                matchState = "match"
            ).also { onu.status = it }
        }
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns onus
        properties.inventory.maxSlotProbe = 1
        stubSignalPollExecute(session)

        val result = service.pollSignals()

        assertEquals(1, result.slotsPolled)
        assertEquals(16, result.portsPolled)
        assertEquals(2, result.onusUpdated)
        assertEquals(0, BigDecimal("-18.54").compareTo(statuses[10L]!!.onuRxDbm))
        assertEquals(0, BigDecimal("-24.82").compareTo(statuses[10L]!!.oltRxDbm))
        assertEquals(0, BigDecimal("-25.08").compareTo(statuses[11L]!!.onuRxDbm))
        assertEquals(0, BigDecimal("-30.46").compareTo(statuses[11L]!!.oltRxDbm))
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

    @Test
    fun `tras timeout en un puerto reentra interface gpon y sigue polleando el resto`() {
        properties.inventory.maxSlotProbe = 1
        properties.inventory.defaultPortsPerGponBoard = 3
        model.maxSlotProbe = 1
        model.defaultPortsPerGponBoard = 3

        val session = mockk<HuaweiCliSession>()
        val commands = mutableListOf<String>()
        every { session.execute(any()) } answers {
            val cmd = firstArg<String>()
            commands += cmd
            when {
                cmd.startsWith("display board") -> "board-output"
                cmd == "display ont optical-info 0 all" -> "opt-0"
                cmd == "display ont optical-info 1 all" -> throw OltCommandTimeoutException("CLI command timed out after 180000ms")
                cmd == "display ont optical-info 2 all" -> "opt-2"
                else -> "ok"
            }
        }
        every { boardParser.parseAll("board-output") } returns listOf(
            ParsedBoard(slot = 1, boardName = "H801GPHF", status = "Normal")
        )
        every { opticalInfoParser.parseAll("opt-0") } returns listOf(
            ParsedOpticalInfo(ontId = 1, rxPowerDbm = -18.0, oltRxPowerDbm = -24.0)
        )
        every { opticalInfoParser.parseAll("opt-2") } returns listOf(
            ParsedOpticalInfo(ontId = 2, rxPowerDbm = -19.0, oltRxPowerDbm = -25.0)
        )
        every { opticalInfoParser.parseAll("ok") } returns emptyList()

        val onu0 = OltMgrOnu(
            id = 10L, sn = "SN10", externalId = "gigafiber-ma5608t_1_0_1",
            olt = olt, board = 1, port = 0, onuIndex = 1
        )
        val onu2 = OltMgrOnu(
            id = 12L, sn = "SN12", externalId = "gigafiber-ma5608t_1_2_2",
            olt = olt, board = 1, port = 2, onuIndex = 2
        )
        val status0 = OltMgrOnuStatusCurrent(onuId = 10L, onu = onu0, runState = "online").also { onu0.status = it }
        val status2 = OltMgrOnuStatusCurrent(onuId = 12L, onu = onu2, runState = "online").also { onu2.status = it }
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu0, onu2)
        stubSignalPollExecute(session)

        val result = service.pollSignals()

        assertEquals(2, result.onusUpdated)
        assertEquals(0, BigDecimal("-24.0").compareTo(status0.oltRxDbm))
        assertEquals(0, BigDecimal("-25.0").compareTo(status2.oltRxDbm))
        val idxAll1 = commands.indexOf("display ont optical-info 1 all")
        val idxAll2 = commands.indexOf("display ont optical-info 2 all")
        val reenterAfterTimeout = commands.withIndex().any { (idx, cmd) ->
            idx > idxAll1 && idx < idxAll2 && cmd == "interface gpon 0/1"
        }
        assertTrue(idxAll1 >= 0 && idxAll2 > idxAll1, "commands=$commands")
        assertTrue(reenterAfterTimeout, "expected re-enter interface gpon after timeout, commands=$commands")
    }

    @Test
    fun `si optical-info all parsea vacio reentra interface y reintenta bulk sin comandos por onu`() {
        properties.inventory.maxSlotProbe = 1
        properties.inventory.defaultPortsPerGponBoard = 1
        model.maxSlotProbe = 1
        model.defaultPortsPerGponBoard = 1

        val commands = mutableListOf<String>()
        val bulkAttempts = AtomicInteger(0)
        val session = mockk<HuaweiCliSession>()
        every { session.execute(any()) } answers {
            val cmd = firstArg<String>()
            commands += cmd
            when {
                cmd.startsWith("display board") -> "board-output"
                cmd == "display ont optical-info 0 all" -> {
                    if (bulkAttempts.getAndIncrement() == 0) {
                        "short-empty-128"
                    } else {
                        "opt-bulk-7"
                    }
                }
                else -> "ok"
            }
        }
        every { boardParser.parseAll("board-output") } returns listOf(
            ParsedBoard(slot = 1, boardName = "H801GPHF", status = "Normal")
        )
        every { opticalInfoParser.parseAll("short-empty-128") } returns emptyList()
        every { opticalInfoParser.parseAll("opt-bulk-7") } returns listOf(
            ParsedOpticalInfo(ontId = 7, rxPowerDbm = -21.0, txPowerDbm = 2.1, oltRxPowerDbm = -25.5, temperatureC = 48.0)
        )

        val onu7 = OltMgrOnu(
            id = 17L, sn = "SN17", externalId = "gigafiber-ma5608t_1_0_7",
            olt = olt, board = 1, port = 0, onuIndex = 7
        )
        val status7 = OltMgrOnuStatusCurrent(onuId = 17L, onu = onu7, runState = "online").also { onu7.status = it }
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu7)
        stubSignalPollExecute(session)

        val result = service.pollSignals()

        assertEquals(1, result.onusUpdated)
        assertEquals(0, BigDecimal("-25.5").compareTo(status7.oltRxDbm))
        assertEquals(2, commands.count { it == "display ont optical-info 0 all" })
        assertTrue(commands.count { it == "interface gpon 0/1" } >= 2)
        assertFalse(commands.any { it.matches(Regex("display ont optical-info 0 \\d+")) })
    }

    @Test
    fun `si optical-info all falla o sigue vacio no usa comandos por onu`() {
        properties.inventory.maxSlotProbe = 1
        properties.inventory.defaultPortsPerGponBoard = 1
        model.maxSlotProbe = 1
        model.defaultPortsPerGponBoard = 1

        val commands = mutableListOf<String>()
        val session = mockk<HuaweiCliSession>()
        every { session.execute(any()) } answers {
            val cmd = firstArg<String>()
            commands += cmd
            when {
                cmd.startsWith("display board") -> "board-output"
                cmd == "display ont optical-info 0 all" ->
                    throw OltCommandTimeoutException("CLI command timed out after 180000ms")
                else -> "ok"
            }
        }
        every { boardParser.parseAll("board-output") } returns listOf(
            ParsedBoard(slot = 1, boardName = "H801GPHF", status = "Normal")
        )
        every { opticalInfoParser.parseAll(any()) } returns emptyList()

        val onu5 = OltMgrOnu(
            id = 15L, sn = "SN15", externalId = "gigafiber-ma5608t_1_0_5",
            olt = olt, board = 1, port = 0, onuIndex = 5
        )
        val onu6 = OltMgrOnu(
            id = 16L, sn = "SN16", externalId = "gigafiber-ma5608t_1_0_6",
            olt = olt, board = 1, port = 0, onuIndex = 6
        )
        val status5 = OltMgrOnuStatusCurrent(onuId = 15L, onu = onu5, runState = "online").also { onu5.status = it }
        val status6 = OltMgrOnuStatusCurrent(onuId = 16L, onu = onu6, runState = "online").also { onu6.status = it }
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu5, onu6)
        stubSignalPollExecute(session)

        val result = service.pollSignals()

        assertEquals(0, result.onusUpdated)
        assertEquals(2, commands.count { it == "display ont optical-info 0 all" })
        assertFalse(commands.any { it.matches(Regex("display ont optical-info 0 \\d+")) })
    }

    @Test
    fun `pollSignals via SNMP actualiza potencias sin CLI`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        properties.snmp.allowSshSignalFallback = false
        val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot = 0, port = 1)
        every { snmpClient.listOptical(null) } returns listOf(
            SnmpOntOptical(
                key = SnmpOntKey(ifIndex = ifIndex, ontId = 3),
                onuRxDbm = -18.5,
                onuTxDbm = 2.1,
                oltRxDbm = -27.45
            )
        )
        val onu = OltMgrOnu(
            id = 30L,
            sn = "SNSNMPOPT1",
            externalId = "gigafiber-ma5608t_0_1_3",
            olt = olt,
            board = 0,
            port = 1,
            onuIndex = 3
        )
        val status = OltMgrOnuStatusCurrent(onuId = 30L, onu = onu, runState = "online")
        onu.status = status
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu)

        val result = service.pollSignals()

        assertNull(result.skippedReason)
        assertEquals(1, result.onusUpdated)
        assertEquals(1, result.slotsPolled)
        assertEquals(1, result.portsPolled)
        assertEquals(BigDecimal("-18.50"), status.onuRxDbm)
        assertEquals(BigDecimal("2.10"), status.onuTxDbm)
        assertEquals(BigDecimal("-27.45"), status.oltRxDbm)
        assertEquals("good", status.signalCategory)
        verify(exactly = 1) { snmpClient.listOptical(null) }
        verify(exactly = 0) { cliBus.execute(any(), any<(HuaweiCliSession) -> Any>()) }
    }

    @Test
    fun `pollSignals SNMP full-table no pasa lista de puertos`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        properties.snmp.opticalPerPortWalks = false
        every { snmpClient.listOptical(null) } returns emptyList()
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns emptyList()

        val result = service.pollSignals()

        assertNull(result.skippedReason)
        assertEquals(0, result.onusUpdated)
        verify(exactly = 1) { snmpClient.listOptical(null) }
    }

    @Test
    fun `pollSignals SNMP scope solo puertos online`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        properties.snmp.opticalPerPortWalks = true
        properties.snmp.opticalOnlineOnly = true
        val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot = 0, port = 1)
        every { snmpClient.listOptical(any()) } returns listOf(
            SnmpOntOptical(
                key = SnmpOntKey(ifIndex = ifIndex, ontId = 3),
                onuRxDbm = -18.5,
                onuTxDbm = 2.1,
                oltRxDbm = -27.45
            )
        )
        val online = OltMgrOnu(
            id = 30L,
            sn = "SNONLINE",
            externalId = "gigafiber-ma5608t_0_1_3",
            olt = olt,
            board = 0,
            port = 1,
            onuIndex = 3
        )
        online.status = OltMgrOnuStatusCurrent(onu = online, runState = "online")
        val offline = OltMgrOnu(
            id = 31L,
            sn = "SNOFFLINE",
            externalId = "gigafiber-ma5608t_0_2_1",
            olt = olt,
            board = 0,
            port = 2,
            onuIndex = 1
        )
        offline.status = OltMgrOnuStatusCurrent(onu = offline, runState = "offline")
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(online, offline)

        service.pollSignals()

        verify {
            snmpClient.listOptical(
                withArg { ports ->
                    org.junit.jupiter.api.Assertions.assertEquals(1, ports?.size)
                    org.junit.jupiter.api.Assertions.assertTrue(ports!!.contains(GponFsp(0, 0, 1)))
                }
            )
        }
    }

    @Test
    fun `pollSignals SNMP skip cuando no hay ONUs en scope`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        properties.snmp.opticalPerPortWalks = true
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns emptyList()

        val result = service.pollSignals()

        assertEquals("no_onus_to_poll", result.skippedReason)
        verify(exactly = 0) { snmpClient.listOptical(any()) }
    }

    @Test
    fun `pollSignals exige SNMP si fallback SSH desactivado`() {
        properties.snmp.enabled = false
        properties.snmp.allowSshSignalFallback = false
        val result = service.pollSignals()
        assertEquals("snmp_required", result.skippedReason)
        verify(exactly = 0) { snmpClient.listOptical(any()) }
        verify(exactly = 0) { cliBus.execute(any(), any<(HuaweiCliSession) -> Any>()) }
    }

    private fun stubSignalPollExecute(session: HuaweiCliSession) {
        every { cliBus.execute(CliJobType.SIGNAL_POLL, any<(HuaweiCliSession) -> Any>()) } answers {
            val block = secondArg<(HuaweiCliSession) -> Any>()
            CliBusResult.Ok(block(session))
        }
    }
}
