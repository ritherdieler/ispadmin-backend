package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSyncRun
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrSyncRunRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.snmp.GponFsp
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpClient
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpFusedSnapshot
import com.dscorp.wispadmin.oltgateway.snmp.SnmpOntKey
import com.dscorp.wispadmin.oltgateway.snmp.SnmpOntOptical
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class OltFusedPassWiringTest {

    private val cliBus = mockk<OltCliBus>()
    private val snmpClient = mockk<OltSnmpClient>()
    private val oltRepository = mockk<OltMgrOltRepository>()
    private val onuRepository = mockk<OltMgrOnuRepository>()
    private val statusRepository = mockk<OltMgrOnuStatusCurrentRepository>()
    private val taskRepository = mockk<OltMgrTaskRepository>()
    private val auditLogRepository = mockk<OltMgrAuditLogRepository>()
    private val syncRunRepository = mockk<OltMgrSyncRunRepository>()
    private val queryFacade = mockk<OltGatewayQueryFacade>()
    private val boardParser = mockk<BoardParser>()
    private val opticalInfoParser = mockk<OpticalInfoParser>()
    private val fusedCache = OltFusedInventoryCache()
    private val idSeq = AtomicLong(100)

    private val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")

    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        inventory.defaultPortsPerGponBoard = 4
        snmp.enabled = true
        snmp.roCommunity = "test-ro"
        snmp.opticalPerPortWalks = true
        snmp.fusedInventoryOptical = true
    }

    private lateinit var signalPoll: OltSignalPollService
    private lateinit var inventorySync: OltInventorySyncService

    private val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot = 0, port = 1)

    private val fusedOnus = listOf(
        ParsedOnuSummary(frame = 0, slot = 0, port = 1, ontId = 3, sn = "VSOL0086F6E9", runState = "online")
    )

    private val fusedOptical = listOf(
        SnmpOntOptical(
            key = SnmpOntKey(ifIndex = ifIndex, ontId = 3),
            onuRxDbm = -18.5,
            onuTxDbm = 2.1,
            oltRxDbm = -27.45
        )
    )

    private val existing = OltMgrOnu(
        id = 30L,
        sn = "VSOL0086F6E9",
        externalId = "gigafiber-ma5608t_0_1_3",
        olt = olt,
        board = 0,
        port = 1,
        onuIndex = 3
    ).apply { status = OltMgrOnuStatusCurrent(onu = this, runState = "online") }

    @BeforeEach
    fun setUp() {
        properties.snmp.opticalPerPortWalks = true
        properties.snmp.opticalParallelPorts = 1
        properties.snmp.fusedInventoryOptical = true
        every { cliBus.queueDepth() } returns 0
        every { cliBus.busyJobType() } returns null
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { taskRepository.existsByStatus("running") } returns false
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) } answers {
            firstArg<Iterable<OltMgrOnu>>().toList()
        }
        every { onuRepository.flush() } returns Unit
        every { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) } answers {
            firstArg<Iterable<OltMgrOnuStatusCurrent>>().toList()
        }
        every { auditLogRepository.saveAll(any<Iterable<OltMgrAuditLog>>()) } answers {
            firstArg<Iterable<OltMgrAuditLog>>().toList()
        }
        every { syncRunRepository.save(any()) } answers {
            firstArg<OltMgrSyncRun>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }

        signalPoll = OltSignalPollService(
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            taskRepository = taskRepository,
            boardParser = boardParser,
            opticalInfoParser = opticalInfoParser,
            signalCategoryCalculator = SignalCategoryCalculator(),
            properties = properties,
            cliBus = cliBus,
            snmpClient = snmpClient,
            fusedInventoryCache = fusedCache,
        )
        inventorySync = OltInventorySyncService(
            queryFacade = queryFacade,
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            auditLogRepository = auditLogRepository,
            syncRunRepository = syncRunRepository,
            taskRepository = taskRepository,
            properties = properties,
            cliBus = cliBus,
            snmpClient = snmpClient,
            fusedInventoryCache = fusedCache,
        )
    }

    @Test
    fun `el poll de senal barre todos los puertos de la placa, no solo los que tienen ONUs`() {
        every { snmpClient.listInventoryAndOptical(any()) } returns
            OltSnmpFusedSnapshot(fusedOnus, fusedOptical, portsAttempted = 4, portsFailed = 0)

        signalPoll.pollSignals()

        verify {
            snmpClient.listInventoryAndOptical(
                withArg { ports ->
                    assertEquals(4, ports.size)
                    assertTrue(ports.containsAll((0 until 4).map { GponFsp(0, 0, it) }))
                }
            )
        }
        verify(exactly = 0) { snmpClient.listOptical(any()) }
    }

    @Test
    fun `la pasada fusionada publica el inventario para el sync`() {
        every { snmpClient.listInventoryAndOptical(any()) } returns
            OltSnmpFusedSnapshot(fusedOnus, fusedOptical, portsAttempted = 4, portsFailed = 0)

        signalPoll.pollSignals()

        val cached = fusedCache.peek()
        assertNotNull(cached)
        assertEquals(fusedOnus, cached!!.onus)
    }

    @Test
    fun `un puerto fallido no publica inventario porque persistSnapshot borraria sus ONUs`() {
        every { snmpClient.listInventoryAndOptical(any()) } returns
            OltSnmpFusedSnapshot(fusedOnus, fusedOptical, portsAttempted = 4, portsFailed = 1)

        signalPoll.pollSignals()

        assertNull(fusedCache.peek(), "a partial scan must never reach persistSnapshot")
    }

    @Test
    fun `el sync de inventario consume el snapshot fusionado sin volver a leer la OLT`() {
        fusedCache.publish(fusedOnus)

        val result = inventorySync.syncInventoryFromSnmp()

        assertNull(result.skippedReason)
        assertNull(result.error)
        verify(exactly = 0) { snmpClient.listConfiguredOnus() }
    }

    @Test
    fun `el consume de cache fusionada corre dentro del lock compartido`() {
        val acquisitions = java.util.concurrent.atomic.AtomicInteger(0)
        val locker = object : com.dscorp.wispadmin.oltgateway.snmp.OltSnmpPollLocker {
            override fun <T> withLock(block: () -> T): T {
                acquisitions.incrementAndGet()
                return block()
            }
        }
        inventorySync = OltInventorySyncService(
            queryFacade = queryFacade,
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            auditLogRepository = auditLogRepository,
            syncRunRepository = syncRunRepository,
            taskRepository = taskRepository,
            properties = properties,
            cliBus = cliBus,
            snmpClient = snmpClient,
            fusedInventoryCache = fusedCache,
            pollLock = locker,
        )
        fusedCache.publish(fusedOnus)

        inventorySync.syncInventoryFromSnmp()

        assertEquals(1, acquisitions.get())
        verify(exactly = 0) { snmpClient.listConfiguredOnus() }
    }

    @Test
    fun `sin snapshot fusionado el sync vuelve al walk full-table`() {
        every { snmpClient.listConfiguredOnus() } returns fusedOnus

        inventorySync.syncInventoryFromSnmp()

        verify(exactly = 1) { snmpClient.listConfiguredOnus() }
    }

    @Test
    fun `despues de esperar el lock relee la cache fusionada y no camina la OLT`() {
        val locker = object : com.dscorp.wispadmin.oltgateway.snmp.OltSnmpPollLocker {
            override fun <T> withLock(block: () -> T): T {
                fusedCache.publish(fusedOnus)
                return block()
            }
        }
        inventorySync = OltInventorySyncService(
            queryFacade = queryFacade,
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            auditLogRepository = auditLogRepository,
            syncRunRepository = syncRunRepository,
            taskRepository = taskRepository,
            properties = properties,
            cliBus = cliBus,
            snmpClient = snmpClient,
            fusedInventoryCache = fusedCache,
            pollLock = locker,
        )
        every { snmpClient.listConfiguredOnus() } returns fusedOnus

        val result = inventorySync.syncInventoryFromSnmp()

        assertNull(result.skippedReason)
        assertNull(result.error)
        verify(exactly = 0) { snmpClient.listConfiguredOnus() }
    }

    @Test
    fun `authorize despues del snapshot fusionado no tombstonea la ONU`() {
        fusedCache.publish(fusedOnus)
        val capturedAt = fusedCache.peek()?.capturedAt ?: Instant.parse("2026-09-09T18:00:00Z")
        val authorized = OltMgrOnu(
            id = 99L,
            sn = "NEWAUTH001",
            externalId = "gigafiber-ma5608t_1_0_0",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 0,
        ).apply {
            createdAt = capturedAt.plusSeconds(30)
            updatedAt = capturedAt.plusSeconds(30)
        }
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing, authorized)
        every { snmpClient.listConfiguredOnus() } returns fusedOnus + ParsedOnuSummary(
            frame = 0,
            slot = 1,
            port = 0,
            ontId = 0,
            sn = "NEWAUTH001",
        )

        val result = inventorySync.syncInventoryFromSnmp()

        assertEquals(0, result.softDeleted)
        assertNull(authorized.deletedAt)
        verify(exactly = 1) { snmpClient.listConfiguredOnus() }
    }

    @Test
    fun `fused corre aunque PER_PORT sea false y PARALLEL_PORTS sea 3`() {
        properties.snmp.opticalPerPortWalks = false
        properties.snmp.opticalParallelPorts = 3
        properties.snmp.fusedInventoryOptical = true
        every { snmpClient.listInventoryAndOptical(any()) } returns
            OltSnmpFusedSnapshot(fusedOnus, fusedOptical, portsAttempted = 4, portsFailed = 0)

        signalPoll.pollSignals()

        verify { snmpClient.listInventoryAndOptical(any()) }
        verify(exactly = 0) { snmpClient.listOptical(any()) }
        assertTrue(fusedCache.peek() != null)
    }
}
