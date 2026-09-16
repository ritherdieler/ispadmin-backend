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
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.parser.ParsedOpticalInfo
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import com.dscorp.wispadmin.oltgateway.snmp.GponFsp
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpClient
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpFusedSnapshot
import com.dscorp.wispadmin.oltgateway.snmp.SnmpOntKey
import com.dscorp.wispadmin.oltgateway.snmp.SnmpOntOptical
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

class OltSignalPollServiceTest {

    private val publisher = mockk<org.springframework.context.ApplicationEventPublisher>(relaxed=true)
    private val platformBus = com.dscorp.wispadmin.events.RecordingEventBus()
    private val cliBus = mockk<OltCliBus>()
    private val snmpClient = mockk<OltSnmpClient>()
    private val oltRepository = mockk<OltMgrOltRepository>()
    private val onuRepository = mockk<OltMgrOnuRepository>()
    private val statusRepository = mockk<OltMgrOnuStatusCurrentRepository>()
    private val taskRepository = mockk<OltMgrTaskRepository>()
    private val signalCategoryCalculator = SignalCategoryCalculator()
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        sync.skipWhenWriteRunning = true
        inventory.maxSlotProbe = 1
        inventory.defaultPortsPerGponBoard = 2
        snmp.enabled = false
        // Full-table SNMP path; per-port / fused tests opt in explicitly.
        snmp.opticalPerPortWalks = false
        snmp.fusedInventoryOptical = false
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
        properties.snmp.roCommunity = ""
        service = OltSignalPollService(
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            taskRepository = taskRepository,
            signalCategoryCalculator = signalCategoryCalculator,
            properties = properties,
            cliBus = cliBus,
            snmpClient = snmpClient,
            eventPublisher = publisher,
            eventBus = platformBus,
        )
        platformBus.published.clear()
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
    fun `optical sin cambios refresca polledAt y publica batch`() {
        val previousPolledAt = Instant.parse("2026-09-01T10:00:00Z")
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
            signalCategory = "good",
            polledAt = previousPolledAt
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

        assertEquals(0, updated.onusUpdated)
        assertEquals(1, updated.polledAtRefreshed)
        assertEquals(0, updated.incompleteDiscarded)
        assertEquals(0, updated.unchangedSkipped)
        assertEquals(1, updated.rowsMatched)
        assertEquals(0, updated.unmatchedRows)
        assertTrue(status.polledAt.isAfter(previousPolledAt))
        verify { publisher.publishEvent(match<OltOpticalObservation> { it.rows.single().optical.rxPowerDbm == -18.54 }) }
        verify { statusRepository.saveAll(match<Iterable<OltMgrOnuStatusCurrent>> { it.single() === status }) }
        assertEquals(0, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL })
        assertEquals(0, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_STATE })
        assertEquals(1, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL_BATCH })
        val batch = platformBus.published.single { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL_BATCH }
        assertTrue(batch.payloadJson.contains("SNOPT001"))
        assertTrue(batch.payloadJson.contains("gigafiber-ma5608t_0_0_1"))
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

        assertEquals(1, updated.onusUpdated)
        assertEquals(0, BigDecimal("-20.00").compareTo(status.onuRxDbm))
        verify(exactly = 0) { statusRepository.findById(any()) }
        verify { statusRepository.saveAll(match<Iterable<OltMgrOnuStatusCurrent>> { it.single() === status }) }
        assertEquals(0, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL })
        assertEquals(0, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_STATE })
        assertEquals(1, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL_BATCH })
        val batch = platformBus.published.single { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL_BATCH }
        assertTrue(batch.payloadJson.contains("SNOPT001"))
        assertTrue(batch.payloadJson.contains("\"slot\":0"))
        assertTrue(batch.payloadJson.contains("\"port\":0"))
    }

    @Test
    fun `optical incompleta con potencia null descarta sin coalesce ni persistir`() {
        val previousPolledAt = Instant.parse("2026-09-01T10:00:00Z")
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
            signalCategory = "good",
            polledAt = previousPolledAt
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

        assertEquals(0, updated.onusUpdated)
        assertEquals(0, updated.polledAtRefreshed)
        assertEquals(1, updated.incompleteDiscarded)
        assertEquals(0, BigDecimal("-18.54").compareTo(status.onuRxDbm))
        assertEquals(0, BigDecimal("2.20").compareTo(status.onuTxDbm))
        assertEquals(0, BigDecimal("-24.82").compareTo(status.oltRxDbm))
        assertEquals(57, status.temperatureC)
        assertEquals(previousPolledAt, status.polledAt)
        verify(exactly = 0) { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) }
        assertEquals(0, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL })
        assertEquals(0, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL_BATCH })
    }

    @Test
    fun `apply by port publishes one optical batch per gpon port and omits incomplete`() {
        val onuA = OltMgrOnu(
            id = 10L,
            sn = "SNA",
            externalId = "gigafiber-ma5608t_0_0_1",
            olt = olt,
            board = 0,
            port = 0,
            onuIndex = 1,
        ).also {
            it.status = OltMgrOnuStatusCurrent(
                onuId = 10L,
                onu = it,
                runState = "online",
                onuRxDbm = BigDecimal("-18.00"),
                onuTxDbm = BigDecimal("2.00"),
                oltRxDbm = BigDecimal("-24.00"),
                signalCategory = "good",
            )
        }
        val onuB = OltMgrOnu(
            id = 11L,
            sn = "SNB",
            externalId = "gigafiber-ma5608t_0_1_1",
            olt = olt,
            board = 0,
            port = 1,
            onuIndex = 1,
        ).also {
            it.status = OltMgrOnuStatusCurrent(
                onuId = 11L,
                onu = it,
                runState = "online",
                onuRxDbm = BigDecimal("-19.00"),
                onuTxDbm = BigDecimal("2.10"),
                oltRxDbm = BigDecimal("-25.00"),
                temperatureC = 51,
                signalCategory = "good",
            )
        }
        val onuIncomplete = OltMgrOnu(
            id = 12L,
            sn = "SNC",
            externalId = "gigafiber-ma5608t_0_0_2",
            olt = olt,
            board = 0,
            port = 0,
            onuIndex = 2,
        ).also {
            it.status = OltMgrOnuStatusCurrent(
                onuId = 12L,
                onu = it,
                runState = "online",
                onuRxDbm = BigDecimal("-17.00"),
                onuTxDbm = BigDecimal("2.00"),
                oltRxDbm = BigDecimal("-23.00"),
                signalCategory = "good",
            )
        }
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onuA, onuB, onuIncomplete)

        val stats = service.applyOpticalUpdatesByPort(
            oltId = 1L,
            rows = listOf(
                OltSignalPollService.OpticalRow(
                    0, 0,
                    ParsedOpticalInfo(1, -18.50, 2.00, -24.00, temperatureC = 50.0),
                ),
                OltSignalPollService.OpticalRow(
                    0, 0,
                    ParsedOpticalInfo(2, -17.00, 2.00, null, temperatureC = 50.0),
                ),
                OltSignalPollService.OpticalRow(
                    0, 1,
                    ParsedOpticalInfo(1, -19.00, 2.10, -25.00, temperatureC = 51.0),
                ),
            ),
        )

        assertEquals(1, stats.onusUpdated)
        assertEquals(1, stats.polledAtRefreshed)
        assertEquals(1, stats.incompleteDiscarded)
        val batches = platformBus.published.filter { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL_BATCH }
        assertEquals(2, batches.size)
        assertTrue(batches[0].payloadJson.contains("\"port\":0"))
        assertTrue(batches[0].payloadJson.contains("SNA"))
        assertFalse(batches[0].payloadJson.contains("SNC"))
        assertTrue(batches[1].payloadJson.contains("\"port\":1"))
        assertTrue(batches[1].payloadJson.contains("SNB"))
    }

    @Test
    fun `optical todo null descarta sin borrar potencias previas`() {
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

        assertEquals(0, updated.onusUpdated)
        assertEquals(0, updated.polledAtRefreshed)
        assertEquals(1, updated.incompleteDiscarded)
        assertEquals(0, BigDecimal("-24.82").compareTo(status.oltRxDbm))
        verify(exactly = 0) { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) }
    }

    @Test
    fun `skip cuando write task running`() {
        every { taskRepository.existsByStatus("running") } returns true

        val result = service.pollSignals()

        assertEquals("write_task_running", result.skippedReason)
        verify(exactly = 0) { cliBus.execute(any(), any<(HuaweiCliSession) -> Any>()) }
    }


    @Test
    fun `skip segundo poll concurrente`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        val onu = OltMgrOnu(
            id = 10L,
            sn = "SN001",
            externalId = "gigafiber-ma5608t_0_1_1",
            olt = olt,
            board = 0,
            port = 1,
            onuIndex = 1
        )
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu)
        val entered = AtomicInteger(0)
        every { snmpClient.listOptical(any()) } answers {
            entered.incrementAndGet()
            val parallel = service.pollSignals()
            assertEquals("sync_already_running", parallel.skippedReason)
            emptyList()
        }

        val result = service.pollSignals()

        assertNull(result.skippedReason)
        assertEquals(1, entered.get())
        assertFalse(service.isRunning())
        verify(exactly = 0) { cliBus.execute(any(), any<(HuaweiCliSession) -> Any>()) }
    }

    @Test
    fun `status incluye lastResult y bus`() {
        every { cliBus.queueDepth() } returns 2
        every { cliBus.busyJobType() } returns CliJobType.INVENTORY
        properties.snmp.enabled = false

        service.pollSignals()
        val status = service.status()

        assertFalse(status.running)
        assertEquals("snmp_required", status.lastResult?.skippedReason)
        assertEquals(2, status.busQueueDepth)
        assertEquals("INVENTORY", status.busBusyJobType)
    }

    @Test
    fun `pollSignals via SNMP actualiza potencias sin CLI`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
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
    fun `pollSignals SNMP registra SSH pressure local al inicio y fin`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        every { cliBus.queueDepth() } returns 4
        every { cliBus.busyJobType() } returns CliJobType.WRITE
        every { snmpClient.listOptical(null) } returns emptyList()
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns emptyList()

        val messages = captureLogs(OltSignalPollService::class.java.name) {
            val result = service.pollSignals()
            assertEquals(4, result.localQueueDepth)
            assertEquals("WRITE", result.localBusyJobType)
        }

        val pressure = messages.filter { it.contains("SNMP_OPTICAL_SSH_PRESSURE") }
        assertEquals(2, pressure.size)
        assertTrue(pressure.any { it.contains("phase=start") })
        assertTrue(pressure.any { it.contains("phase=end") })
        pressure.forEach { line ->
            assertTrue(line.contains("localCliBus=true"))
            assertTrue(line.contains("localQueueDepth=4"))
            assertTrue(line.contains("localBusyJobType=WRITE"))
            assertTrue(line.contains("sshActive=n/a"))
            assertTrue(line.contains("sshMax=n/a"))
        }
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

    @Test
    fun `pollSignals SNMP full-table no pasa lista de puertos`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        properties.snmp.opticalPerPortWalks = false
        properties.snmp.fusedInventoryOptical = false
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
        properties.snmp.fusedInventoryOptical = false
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
    fun `pollSignals exige SNMP`() {
        properties.snmp.enabled = false
        val result = service.pollSignals()
        assertEquals("snmp_required", result.skippedReason)
        verify(exactly = 0) { snmpClient.listOptical(any()) }
        verify(exactly = 0) { cliBus.execute(any(), any<(HuaweiCliSession) -> Any>()) }
    }

    @Test
    fun `pollSignals SNMP adquiere lock y lo libera`() {
        val held = AtomicInteger(0)
        val locker = object : com.dscorp.wispadmin.oltgateway.snmp.OltSnmpPollLocker {
            override fun <T> withLock(block: () -> T): T {
                held.incrementAndGet()
                return try {
                    block()
                } finally {
                    held.decrementAndGet()
                }
            }
        }
        service = OltSignalPollService(
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            taskRepository = taskRepository,
            signalCategoryCalculator = signalCategoryCalculator,
            properties = properties,
            cliBus = cliBus,
            snmpClient = snmpClient,
            eventPublisher = publisher,
            eventBus = platformBus,
            pollLock = locker,
        )
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        every { snmpClient.listOptical(null) } returns emptyList()
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns emptyList()

        val result = service.pollSignals()

        assertNull(result.skippedReason)
        assertEquals(0, held.get())
        verify(exactly = 1) { snmpClient.listOptical(null) }
    }

    @Test
    fun `fused batch prefers snmp runState over stale status_current`() {
        val onu = OltMgrOnu(
            id = 10L,
            sn = "VSOL0086F6E9",
            externalId = "gigafiber-ma5608t_0_1_3",
            olt = olt,
            board = 0,
            port = 1,
            onuIndex = 3,
        ).also {
            it.status = OltMgrOnuStatusCurrent(onu = it, runState = "offline")
        }
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(onu)
        every { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) } answers {
            firstArg<Iterable<OltMgrOnuStatusCurrent>>().toList()
        }

        val stats = service.applyOpticalUpdates(
            oltId = 1L,
            rows = listOf(
                OltSignalPollService.OpticalRow(
                    slot = 0,
                    port = 1,
                    optical = ParsedOpticalInfo(
                        ontId = 3,
                        rxPowerDbm = -18.5,
                        txPowerDbm = 2.1,
                        oltRxPowerDbm = -27.45,
                    ),
                ),
            ),
            runStateByOnt = mapOf(Triple(0, 1, 3) to "online"),
        )

        assertEquals(1, stats.rowsMatched)
        assertEquals(setOf("VSOL0086F6E9"), stats.publishedOpticalSns)
        val batch = platformBus.published.single { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL_BATCH }
        assertTrue(batch.payloadJson.contains("\"runState\":\"online\""))
        assertEquals(0, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_STATE })
    }

    @Test
    fun `fused poll publishes onu state for ont without optical powers`() {
        val online = OltMgrOnu(
            id = 30L,
            sn = "VSOL0086F6E9",
            externalId = "gigafiber-ma5608t_0_1_3",
            olt = olt,
            board = 0,
            port = 1,
            onuIndex = 3,
        ).also { it.status = OltMgrOnuStatusCurrent(onu = it, runState = "online") }
        val offline = OltMgrOnu(
            id = 31L,
            sn = "ZTEGDC47BFFD",
            externalId = "gigafiber-ma5608t_0_1_4",
            olt = olt,
            board = 0,
            port = 1,
            onuIndex = 4,
        ).also { it.status = OltMgrOnuStatusCurrent(onu = it, runState = "offline") }
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { taskRepository.existsByStatus("running") } returns false
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(online, offline)
        every { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) } answers {
            firstArg<Iterable<OltMgrOnuStatusCurrent>>().toList()
        }
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        properties.snmp.opticalPerPortWalks = true
        properties.snmp.fusedInventoryOptical = true
        properties.inventory.defaultPortsPerGponBoard = 2
        val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot = 0, port = 1)
        every { snmpClient.listInventoryAndOptical(any()) } returns OltSnmpFusedSnapshot(
            onus = listOf(
                ParsedOnuSummary(
                    frame = 0, slot = 0, port = 1, ontId = 3, sn = "VSOL0086F6E9", runState = "online",
                ),
                ParsedOnuSummary(
                    frame = 0, slot = 0, port = 1, ontId = 4, sn = "ZTEGDC47BFFD", runState = "offline",
                ),
            ),
            optical = listOf(
                SnmpOntOptical(
                    key = SnmpOntKey(ifIndex = ifIndex, ontId = 3),
                    onuRxDbm = -18.5,
                    onuTxDbm = 2.1,
                    oltRxDbm = -27.45,
                ),
            ),
            portsAttempted = 2,
            portsFailed = 0,
        )

        val result = service.pollSignals()

        assertNull(result.skippedReason)
        assertEquals(1, platformBus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_OPTICAL_BATCH })
        val states = platformBus.published.filter { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.ONU_STATE }
        assertEquals(1, states.size)
        assertEquals("ZTEGDC47BFFD", states.single().sn)
        assertTrue(states.single().payloadJson.contains("\"runState\":\"offline\""))
    }

}
