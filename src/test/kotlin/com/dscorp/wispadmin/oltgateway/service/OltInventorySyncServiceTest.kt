package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuType
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSyncRun
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuTypeRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrSyncRunRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuFilter
import com.dscorp.wispadmin.oltgateway.exception.CliBusBusyException
import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpClient
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class OltInventorySyncServiceTest {

    private val queryFacade = mockk<OltGatewayQueryFacade>()
    private val oltRepository = mockk<OltMgrOltRepository>()
    private val onuRepository = mockk<OltMgrOnuRepository>()
    private val statusRepository = mockk<OltMgrOnuStatusCurrentRepository>()
    private val auditLogRepository = mockk<OltMgrAuditLogRepository>()
    private val syncRunRepository = mockk<OltMgrSyncRunRepository>()
    private val taskRepository = mockk<OltMgrTaskRepository>()
    private val onuTypeRepository = mockk<OltMgrOnuTypeRepository>()
    private val cliBus = mockk<OltCliBus>()
    private val snmpClient = mockk<OltSnmpClient>()
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        sync.skipWhenWriteRunning = true
        // Legacy unit tests mock SSH inventory; production path is SNMP-first.
        snmp.enabled = false
        snmp.allowSshInventoryFallback = true
    }

    private lateinit var service: OltInventorySyncService
    private val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")
    private val idSeq = AtomicLong(100)

    @BeforeEach
    fun setUp() {
        every { cliBus.queueDepth() } returns 0
        every { cliBus.busyJobType() } returns null
        service = OltInventorySyncService(
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
            onuTypeRepository = onuTypeRepository
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { taskRepository.existsByStatus("running") } returns false
        every { syncRunRepository.save(any()) } answers {
            firstArg<OltMgrSyncRun>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }
        every { auditLogRepository.save(any()) } answers {
            firstArg<OltMgrAuditLog>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }
        every { statusRepository.save(any()) } answers { firstArg() }
        every { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) } answers {
            firstArg<Iterable<OltMgrOnuStatusCurrent>>().toList()
        }
        every { statusRepository.findById(any()) } returns Optional.empty()
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns emptyList()
        every { onuRepository.findByOlt_Id(1L) } returns emptyList()
        every { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) } answers {
            firstArg<Iterable<OltMgrOnu>>().toList()
        }
        every { onuRepository.save(any()) } answers {
            firstArg<OltMgrOnu>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }
        every { onuRepository.flush() } returns Unit
        every { auditLogRepository.saveAll(any<Iterable<OltMgrAuditLog>>()) } answers {
            firstArg<Iterable<OltMgrAuditLog>>().toList()
        }
    }

    @Test
    fun `listCatalogs expone solo tipos usados por el atributo Type de las ONUs`() {
        val usedType = OltMgrOnuType(id = 20L, name = "EG8145V5")
        val unusedType = OltMgrOnuType(id = 21L, name = "HG8245H")
        every { oltRepository.findAll() } returns listOf(olt)
        every { onuRepository.findDistinctUsedOnuTypes() } returns listOf(usedType)
        every { onuTypeRepository.findAll() } returns listOf(usedType, unusedType)
        every { onuRepository.findDistinctVlans() } returns emptyList()
        every { onuRepository.findDistinctProfiles() } returns emptyList()
        every { onuRepository.findDistinctSplitterIds() } returns emptyList()
        every { onuRepository.findDistinctPonTypes() } returns listOf("gpon")

        val types = service.listCatalogs().onuTypes

        assertEquals(listOf("EG8145V5"), types.map { it.name })
    }

    @Test
    fun `insert crea onu importada y status`() {
        every { queryFacade.listOnusParsed() } returns listOf(
            summary(sn = "SNINSERT01", slot = 0, port = 2, ontId = 1, runState = "online", description = "cli-name")
        )
        val saved = slot<Iterable<OltMgrOnu>>()
        every { onuRepository.saveAll(capture(saved)) } answers { firstArg<Iterable<OltMgrOnu>>().toList() }

        val result = service.syncInventory()

        assertEquals(1, result.inserted)
        assertEquals(0, result.updated)
        assertNull(result.skippedReason)
        val savedOnu = saved.captured.single()
        assertEquals("SNINSERT01", savedOnu.sn)
        assertTrue(savedOnu.importedFromOlt)
        assertFalse(savedOnu.syncedAfterImport)
        assertEquals("cli-name", savedOnu.name)
        assertEquals("gigafiber-ma5608t_0_2_1", savedOnu.externalId)
        assertEquals(olt, savedOnu.olt)
        assertNotNull(savedOnu.status)
        assertEquals("online", savedOnu.status?.runState)
    }

    @Test
    fun `syncInventoryFromSnmp usa cliente SNMP y no SSH`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        every { snmpClient.listConfiguredOnus() } returns listOf(
            summary(sn = "SNSNMP0001", slot = 1, port = 0, ontId = 2, runState = "online")
        )
        val saved = slot<Iterable<OltMgrOnu>>()
        every { onuRepository.saveAll(capture(saved)) } answers { firstArg<Iterable<OltMgrOnu>>().toList() }

        val result = service.syncInventoryFromSnmp()

        assertEquals(1, result.inserted)
        assertNull(result.skippedReason)
        assertEquals("SNSNMP0001", saved.captured.single().sn)
        verify(exactly = 1) { snmpClient.listConfiguredOnus() }
        verify(exactly = 0) { queryFacade.listOnusParsed() }
    }

    @Test
    fun `syncInventory usa SNMP cuando snmp enabled (SSH deprecado)`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        properties.snmp.allowSshInventoryFallback = false
        every { snmpClient.listConfiguredOnus() } returns listOf(
            summary(sn = "SNSCHED001", slot = 0, port = 1, ontId = 0, runState = "offline")
        )
        every { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) } answers {
            firstArg<Iterable<OltMgrOnu>>().toList()
        }

        val result = service.syncInventory()

        assertEquals(1, result.inserted)
        verify(exactly = 1) { snmpClient.listConfiguredOnus() }
        verify(exactly = 0) { queryFacade.listOnusParsed() }
    }

    @Test
    fun `syncInventory exige SNMP si fallback SSH desactivado`() {
        properties.snmp.enabled = false
        properties.snmp.allowSshInventoryFallback = false
        val result = service.syncInventory()
        assertEquals("snmp_required", result.skippedReason)
        verify(exactly = 0) { queryFacade.listOnusParsed() }
        verify(exactly = 0) { snmpClient.listConfiguredOnus() }
    }

    @Test
    fun `syncInventoryFromSnmp skipped si snmp disabled`() {
        properties.snmp.enabled = false
        val result = service.syncInventoryFromSnmp()
        assertEquals("snmp_required", result.skippedReason)
        verify(exactly = 0) { snmpClient.listConfiguredOnus() }
    }

    @Test
    fun `SNMP empareja SN hex CLI con vendor y migra a formato canonico`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        val existing = OltMgrOnu(
            id = 21L,
            sn = "56534F4C0086F6E9",
            externalId = "gigafiber-ma5608t_0_0_0",
            olt = olt,
            board = 0,
            port = 0,
            onuIndex = 0,
            name = "legacy-hex",
            importedFromOlt = true,
            status = OltMgrOnuStatusCurrent(
                onu = OltMgrOnu(id = 21L, sn = "56534F4C0086F6E9", externalId = "x", olt = olt),
                onuId = 21L,
                runState = "offline"
            )
        )
        existing.status?.onu = existing
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { snmpClient.listConfiguredOnus() } returns listOf(
            summary(sn = "VSOL0086F6E9", slot = 0, port = 0, ontId = 0, runState = "online")
        )
        val saved = slot<Iterable<OltMgrOnu>>()
        every { onuRepository.saveAll(capture(saved)) } answers { firstArg<Iterable<OltMgrOnu>>().toList() }

        val result = service.syncInventoryFromSnmp()

        assertEquals(0, result.inserted)
        assertEquals(1, result.updated)
        assertEquals(0, result.softDeleted)
        assertEquals("VSOL0086F6E9", existing.sn)
        assertEquals("online", existing.status?.runState)
        assertTrue(saved.captured.any { it.id == 21L && it.sn == "VSOL0086F6E9" })
    }

    @Test
    fun `soft-deleted previos que ocupan FSP se tombstonean antes de insert`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        val deleted = OltMgrOnu(
            id = 205L,
            sn = "54504C4754742A88",
            externalId = "gigafiber-ma5608t_1_1_16",
            olt = olt,
            board = 1,
            port = 1,
            onuIndex = 16,
            importedFromOlt = true,
            deletedAt = Instant.parse("2026-08-04T09:53:34Z")
        )
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(deleted)
        every { snmpClient.listConfiguredOnus() } returns listOf(
            summary(sn = "HWTCNEWA0001", slot = 1, port = 1, ontId = 16, runState = "online")
        )
        val savedBatches = mutableListOf<List<OltMgrOnu>>()
        every { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) } answers {
            val list = firstArg<Iterable<OltMgrOnu>>().toList()
            savedBatches += list
            list
        }

        val result = service.syncInventoryFromSnmp()

        assertEquals(1, result.inserted)
        assertEquals(0, result.softDeleted)
        assertTrue(deleted.board < 0)
        assertTrue(deleted.externalId.contains("deleted"))
        assertTrue(savedBatches.first().any { it.id == 205L && it.board < 0 })
    }

    @Test
    fun `soft delete libera posicion unique para insert en mismo sync`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        val existing = OltMgrOnu(
            id = 22L,
            sn = "485754430000F944",
            externalId = "gigafiber-ma5608t_1_0_25",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 25,
            importedFromOlt = true
        )
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { snmpClient.listConfiguredOnus() } returns listOf(
            summary(sn = "HWTCNEW00001", slot = 1, port = 0, ontId = 25, runState = "online")
        )
        val savedBatches = mutableListOf<List<OltMgrOnu>>()
        every { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) } answers {
            val list = firstArg<Iterable<OltMgrOnu>>().toList()
            savedBatches += list
            list
        }

        val result = service.syncInventoryFromSnmp()

        assertEquals(1, result.softDeleted)
        assertEquals(1, result.inserted)
        assertNotNull(existing.deletedAt)
        // Tombstone must leave (1,0,25) free before insert flush
        assertTrue(existing.board < 0 || existing.externalId.contains("deleted"))
        assertTrue(savedBatches.size >= 2, "expected phased flush, got ${savedBatches.size}")
        assertTrue(savedBatches.first().any { it.id == 22L && it.deletedAt != null })
        assertTrue(savedBatches.last().any { it.sn == "HWTCNEW00001" && it.board == 1 && it.port == 0 && it.onuIndex == 25 })
    }

    @Test
    fun `swap de posiciones entre dos ONUs no choca unique`() {
        properties.snmp.enabled = true
        properties.snmp.roCommunity = "test-ro"
        val a = OltMgrOnu(
            id = 31L,
            sn = "SNSWAP0000A",
            externalId = "gigafiber-ma5608t_1_0_1",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 1,
            importedFromOlt = true
        )
        val b = OltMgrOnu(
            id = 32L,
            sn = "SNSWAP0000B",
            externalId = "gigafiber-ma5608t_1_0_2",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 2,
            importedFromOlt = true
        )
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(a, b)
        every { snmpClient.listConfiguredOnus() } returns listOf(
            summary(sn = "SNSWAP0000A", slot = 1, port = 0, ontId = 2, runState = "online"),
            summary(sn = "SNSWAP0000B", slot = 1, port = 0, ontId = 1, runState = "online")
        )
        val savedBatches = mutableListOf<List<OltMgrOnu>>()
        every { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) } answers {
            val list = firstArg<Iterable<OltMgrOnu>>().toList()
            savedBatches += list
            list
        }

        val result = service.syncInventoryFromSnmp()

        assertEquals(0, result.inserted)
        assertEquals(2, result.updated)
        assertEquals(2, a.onuIndex)
        assertEquals(1, b.onuIndex)
        assertTrue(savedBatches.size >= 2, "expected staging flush before final positions")
    }

    @Test
    fun `update importedFromOlt actualiza nombre y posicion`() {
        val existing = OltMgrOnu(
            id = 11L,
            sn = "SNUPDATE01",
            externalId = "gigafiber-ma5608t_0_2_1",
            olt = olt,
            board = 0,
            port = 2,
            onuIndex = 1,
            name = "old",
            importedFromOlt = true,
            status = OltMgrOnuStatusCurrent(
                onu = OltMgrOnu(id = 11L, sn = "SNUPDATE01", externalId = "x", olt = olt),
                onuId = 11L,
                runState = "online",
                matchState = "match"
            )
        )
        existing.status?.onu = existing
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(existing)
        every { onuRepository.findBySn("SNUPDATE01") } returns Optional.of(existing)
        every { queryFacade.listOnusParsed() } returns listOf(
            summary(sn = "SNUPDATE01", slot = 1, port = 5, ontId = 3, runState = "offline", description = "new-cli")
        )
        every { onuRepository.save(any()) } answers { firstArg() }
        val savedStatuses = slot<Iterable<OltMgrOnuStatusCurrent>>()
        every { statusRepository.saveAll(capture(savedStatuses)) } answers {
            firstArg<Iterable<OltMgrOnuStatusCurrent>>().toList()
        }

        val result = service.syncInventory()

        assertEquals(0, result.inserted)
        assertEquals(1, result.updated)
        assertEquals(1, existing.board)
        assertEquals(5, existing.port)
        assertEquals(3, existing.onuIndex)
        assertEquals("new-cli", existing.name)
        assertEquals("gigafiber-ma5608t_1_5_3", existing.externalId)
        assertEquals("offline", savedStatuses.captured.single().runState)
        verify { auditLogRepository.saveAll(match<Iterable<OltMgrAuditLog>> { it.single().action == "sync_position_changed" }) }
    }

    @Test
    fun `update importedFromOlt false no pisa campos CRM`() {
        val zone = OltMgrZone(id = 7L, name = "ZonaCRM")
        val existing = OltMgrOnu(
            id = 12L,
            sn = "SNCRM00001",
            externalId = "gigafiber-ma5608t_0_2_1",
            olt = olt,
            board = 0,
            port = 2,
            onuIndex = 1,
            name = "crm-name",
            address = "Calle 1",
            contact = "999",
            zone = zone,
            importedFromOlt = false
        )
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(existing)
        every { onuRepository.findBySn("SNCRM00001") } returns Optional.of(existing)
        every { queryFacade.listOnusParsed() } returns listOf(
            summary(sn = "SNCRM00001", slot = 0, port = 3, ontId = 2, runState = "online", description = "cli-desc")
        )
        every { onuRepository.save(any()) } answers { firstArg() }

        service.syncInventory()

        assertEquals("crm-name", existing.name)
        assertEquals("Calle 1", existing.address)
        assertEquals("999", existing.contact)
        assertEquals(7L, existing.zone?.id)
        assertEquals(0, existing.board)
        assertEquals(3, existing.port)
        assertEquals(2, existing.onuIndex)
    }

    @Test
    fun `no soft delete cuando snapshot CLI vacio`() {
        val existing = OltMgrOnu(
            id = 12L,
            sn = "SNKEEP01",
            externalId = "gigafiber-ma5608t_0_1_1",
            olt = olt,
            board = 0,
            port = 1,
            onuIndex = 1,
            name = "keep",
            importedFromOlt = true
        )
        every { queryFacade.listOnusParsed() } returns emptyList()
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(existing)

        val result = service.syncInventory()

        assertEquals("empty_snapshot", result.skippedReason)
        assertEquals(0, result.softDeleted)
        verify(exactly = 0) { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) }
    }

    @Test
    fun `soft delete cuando falta en snapshot CLI`() {
        val existing = OltMgrOnu(
            id = 13L,
            sn = "SNMISSING1",
            externalId = "gigafiber-ma5608t_0_2_1",
            olt = olt,
            board = 0,
            port = 2,
            onuIndex = 1,
            importedFromOlt = true
        )
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(existing)
        every { queryFacade.listOnusParsed() } returns listOf(
            summary(sn = "SNOTHER01", slot = 0, port = 1, ontId = 1, runState = "online")
        )
        every { onuRepository.save(any()) } answers { firstArg() }

        val result = service.syncInventory()

        assertEquals(1, result.softDeleted)
        assertEquals(1, result.inserted)
        assertNotNull(existing.deletedAt)
        verify { auditLogRepository.saveAll(match<Iterable<OltMgrAuditLog>> { it.single().action == "sync_missing_on_olt" && it.single().onu?.id == 13L }) }
    }

    @Test
    fun `reappear limpia deletedAt`() {
        val deletedAt = Instant.parse("2026-01-01T00:00:00Z")
        val existing = OltMgrOnu(
            id = 14L,
            sn = "SNREAPPEAR",
            externalId = "gigafiber-ma5608t_0_2_1",
            olt = olt,
            board = 0,
            port = 2,
            onuIndex = 1,
            importedFromOlt = true,
            deletedAt = deletedAt
        )
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(existing)
        every { onuRepository.findBySn("SNREAPPEAR") } returns Optional.of(existing)
        every { queryFacade.listOnusParsed() } returns listOf(
            summary(sn = "SNREAPPEAR", slot = 0, port = 2, ontId = 1, runState = "online")
        )
        every { onuRepository.save(any()) } answers { firstArg() }

        val result = service.syncInventory()

        assertEquals(1, result.updated)
        assertNull(existing.deletedAt)
        assertTrue(existing.importedFromOlt)
    }

    @Test
    fun `unchanged no persiste onu ni status`() {
        val existing = OltMgrOnu(
            id = 15L,
            sn = "SNSTABLE01",
            externalId = "gigafiber-ma5608t_0_2_1",
            olt = olt,
            board = 0,
            port = 2,
            onuIndex = 1,
            name = "same",
            importedFromOlt = true,
            status = OltMgrOnuStatusCurrent(
                onu = OltMgrOnu(id = 15L, sn = "SNSTABLE01", externalId = "x", olt = olt),
                onuId = 15L,
                runState = "online",
                matchState = "match"
            )
        )
        existing.status?.onu = existing
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(existing)
        every { queryFacade.listOnusParsed() } returns listOf(
            summary(sn = "SNSTABLE01", slot = 0, port = 2, ontId = 1, runState = "online", matchState = "match", description = "same")
        )

        val result = service.syncInventory()

        assertEquals(1, result.unchanged)
        assertEquals(0, result.updated)
        verify(exactly = 0) { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) }
        verify(exactly = 0) { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) }
        verify(exactly = 0) { statusRepository.findById(any()) }
    }

    @Test
    fun `bulk unchanged no invoca saveAll de onu ni status`() {
        val existing = (1..100).map { index ->
            OltMgrOnu(
                id = index.toLong(),
                sn = "SNBULK%03d".format(index),
                externalId = "gigafiber-ma5608t_0_1_$index",
                olt = olt,
                board = 0,
                port = 1,
                onuIndex = index,
                name = "onu-$index",
                importedFromOlt = true
            ).also { onu ->
                onu.status = OltMgrOnuStatusCurrent(
                    onu = onu,
                    onuId = onu.id,
                    runState = "online",
                    matchState = "match"
                )
            }
        }
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns existing
        every { onuRepository.findByOlt_Id(1L) } returns existing
        every { queryFacade.listOnusParsed() } returns existing.map { onu ->
            summary(
                sn = onu.sn,
                slot = onu.board,
                port = onu.port,
                ontId = onu.onuIndex,
                runState = "online",
                matchState = "match",
                description = onu.name
            )
        }

        val result = service.syncInventory()

        assertEquals(100, result.unchanged)
        assertEquals(0, result.updated)
        verify(exactly = 0) { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) }
        verify(exactly = 0) { statusRepository.saveAll(any<Iterable<OltMgrOnuStatusCurrent>>()) }
        verify(exactly = 0) { statusRepository.findById(any()) }
    }

    @Test
    fun `cambio solo de status persiste status sin tocar onu`() {
        val existing = OltMgrOnu(
            id = 20L,
            sn = "SNSTATUS01",
            externalId = "gigafiber-ma5608t_0_2_1",
            olt = olt,
            board = 0,
            port = 2,
            onuIndex = 1,
            name = "same",
            importedFromOlt = true,
            status = OltMgrOnuStatusCurrent(
                onu = OltMgrOnu(id = 20L, sn = "SNSTATUS01", externalId = "x", olt = olt),
                onuId = 20L,
                runState = "online",
                matchState = "match"
            )
        )
        existing.status?.onu = existing
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns listOf(existing)
        every { onuRepository.findByOlt_Id(1L) } returns listOf(existing)
        every { queryFacade.listOnusParsed() } returns listOf(
            summary(sn = "SNSTATUS01", slot = 0, port = 2, ontId = 1, runState = "offline", matchState = "match", description = "same")
        )
        val savedStatuses = slot<Iterable<OltMgrOnuStatusCurrent>>()
        every { statusRepository.saveAll(capture(savedStatuses)) } answers {
            firstArg<Iterable<OltMgrOnuStatusCurrent>>().toList()
        }

        val result = service.syncInventory()

        assertEquals(1, result.updated)
        verify(exactly = 0) { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) }
        assertEquals("offline", savedStatuses.captured.single().runState)
    }

    @Test
    fun `skip cuando hay write task running`() {
        every { taskRepository.existsByStatus("running") } returns true

        val result = service.syncInventory()

        assertEquals("write_task_running", result.skippedReason)
        verify(exactly = 0) { queryFacade.listOnusParsed() }
    }

    @Test
    fun `skip segundo sync concurrente`() {
        every { queryFacade.listOnusParsed() } answers {
            val parallel = service.syncInventory()
            assertEquals("sync_already_running", parallel.skippedReason)
            listOf(summary(sn = "SNONLY01", slot = 0, port = 1, ontId = 1, runState = "online"))
        }
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns emptyList()
        every { onuRepository.findByOlt_Id(1L) } returns emptyList()
        every { onuRepository.saveAll(any<Iterable<OltMgrOnu>>()) } answers {
            firstArg<Iterable<OltMgrOnu>>().onEach { if (it.id == null) it.id = 88L }.toList()
        }

        val result = service.syncInventory()

        assertNull(result.skippedReason)
        assertEquals(1, result.inserted)
        assertFalse(service.isRunning())
    }

    @Test
    fun `skip cuando el bus CLI rechaza inventory`() {
        every { queryFacade.listOnusParsed() } throws CliBusBusyException("already_queued")

        val result = service.syncInventory()

        assertEquals("already_queued", result.skippedReason)
        assertFalse(service.isRunning())
    }

    @Test
    fun `skip cuando la OLT no es alcanzable`() {
        every { queryFacade.listOnusParsed() } throws CliBusBusyException("olt_unreachable")

        val result = service.syncInventory()

        assertEquals("olt_unreachable", result.skippedReason)
        assertFalse(service.isRunning())
    }

    @Test
    fun `skip cuando topology discovery reporta OLT inalcanzable`() {
        every { queryFacade.listOnusParsed() } throws OltUnreachableException("Unable to reach OLT at 10.11.104.2:22")

        val result = service.syncInventory()

        assertEquals("olt_unreachable", result.skippedReason)
        assertFalse(service.isRunning())
    }

    @Test
    fun `status incluye profundidad y tipo de job del bus`() {
        every { cliBus.queueDepth() } returns 3
        every { cliBus.busyJobType() } returns CliJobType.SIGNAL_POLL

        val status = service.status()

        assertEquals(3, status.busQueueDepth)
        assertEquals("SIGNAL_POLL", status.busBusyJobType)
    }

    @Test
    fun `persiste sync run con conteos`() {
        every { queryFacade.listOnusParsed() } returns emptyList()
        every { onuRepository.findByOlt_IdWithStatus(1L) } returns emptyList()
        every { onuRepository.findByOlt_Id(1L) } returns emptyList()
        val run = slot<OltMgrSyncRun>()
        every { syncRunRepository.save(capture(run)) } answers {
            firstArg<OltMgrSyncRun>().also { if (it.id == null) it.id = 1L }
        }

        service.syncInventory()

        assertNotNull(run.captured.startedAt)
        assertNotNull(run.captured.finishedAt)
        assertTrue(run.captured.durationMs >= 0)
    }

    @Test
    fun `listConfigured ordena por authorizationDate descendente por defecto`() {
        val pageableSlot = slot<org.springframework.data.domain.Pageable>()
        every {
            onuRepository.findConfiguredFiltered(
                q = null,
                board = null,
                port = null,
                oltId = null,
                zoneId = null,
                vlan = null,
                onuTypeId = null,
                onuTypeName = null,
                customProfile = null,
                ponType = null,
                mode = null,
                runState = null,
                signalCategory = null,
                splitterId = null,
                configurationMethod = null,
                wanMode = null,
                mgmtIpMode = null,
                importedSynced = null,
                lastResyncFailed = null,
                lineProfileMaptype = null,
                administrativeStatus = null,
                lastDownCause = null,
                pageable = capture(pageableSlot)
            )
        } returns org.springframework.data.domain.PageImpl(
            emptyList(),
            PageRequest.of(0, 50),
            0
        )

        service.listConfigured(page = 0, size = 50)

        val order = pageableSlot.captured.sort.getOrderFor("authorizationDate")
        assertNotNull(order)
        assertEquals(Sort.Direction.DESC, order!!.direction)
    }

    @Test
    fun `listConfigured aplica filtros y pagina`() {
        val page = org.springframework.data.domain.PageImpl(
            listOf(
                OltMgrOnu(
                    id = 10L,
                    sn = "HWTCABCDEF01",
                    externalId = "ext-10",
                    olt = olt,
                    board = 1,
                    port = 2,
                    onuIndex = 3,
                    name = "Cliente A",
                    importedFromOlt = true
                ).also {
                    it.status = OltMgrOnuStatusCurrent(
                        onu = it,
                        runState = "online",
                        matchState = "match",
                        polledAt = Instant.parse("2026-08-27T12:00:00Z"),
                        onuRxDbm = java.math.BigDecimal("-18.5"),
                        signalCategory = "Good"
                    )
                }
            ),
            org.springframework.data.domain.PageRequest.of(0, 20),
            1
        )
        every {
            onuRepository.findConfiguredFiltered(
                q = "HWTC",
                board = 1,
                port = 2,
                oltId = null,
                zoneId = null,
                vlan = null,
                onuTypeId = null,
                onuTypeName = null,
                customProfile = null,
                ponType = null,
                mode = null,
                runState = "online",
                signalCategory = "Good",
                splitterId = null,
                configurationMethod = null,
                wanMode = null,
                mgmtIpMode = null,
                importedSynced = null,
                lastResyncFailed = null,
                lineProfileMaptype = null,
                administrativeStatus = null,
                lastDownCause = null,
                pageable = any()
            )
        } returns page

        val result = service.listConfigured(
            page = 0,
            size = 20,
            filter = ConfiguredOnuFilter(
                q = "HWTC",
                board = 1,
                port = 2,
                runState = "online",
                signalCategory = "Good"
            )
        )

        assertEquals(1, result.items.size)
        assertEquals("HWTCABCDEF01", result.items[0].sn)
        assertEquals("online", result.items[0].runState)
        assertEquals("good", result.items[0].signalCategory)
        assertEquals(-18.5, result.items[0].onuRxDbm)
        assertEquals(1, result.totalElements)
        verify(exactly = 1) {
            onuRepository.findConfiguredFiltered(
                "HWTC", 1, 2, null, null, null, null, null, null, null, null,
                "online", "Good", null, null, null, null, null, null, null, null, null, any()
            )
        }
    }

    @Test
    fun `listConfigured recalcula signalCategory desde onuRxDbm aunque DB este stale`() {
        val page = org.springframework.data.domain.PageImpl(
            listOf(
                OltMgrOnu(
                    id = 11L,
                    sn = "VSOL00872399",
                    externalId = "ext-11",
                    olt = olt,
                    board = 0,
                    port = 0,
                    onuIndex = 1,
                    importedFromOlt = true
                ).also {
                    it.status = OltMgrOnuStatusCurrent(
                        onu = it,
                        runState = "online",
                        polledAt = Instant.parse("2026-08-27T12:00:00Z"),
                        onuRxDbm = java.math.BigDecimal("-2.01"),
                        signalCategory = "critical"
                    )
                }
            ),
            org.springframework.data.domain.PageRequest.of(0, 20),
            1
        )
        every {
            onuRepository.findConfiguredFiltered(
                q = null,
                board = null,
                port = null,
                oltId = null,
                zoneId = null,
                vlan = null,
                onuTypeId = null,
                onuTypeName = null,
                customProfile = null,
                ponType = null,
                mode = null,
                runState = null,
                signalCategory = null,
                splitterId = null,
                configurationMethod = null,
                wanMode = null,
                mgmtIpMode = null,
                importedSynced = null,
                lastResyncFailed = null,
                lineProfileMaptype = null,
                administrativeStatus = null,
                lastDownCause = null,
                pageable = any()
            )
        } returns page

        val result = service.listConfigured(page = 0, size = 20)

        assertEquals("good", result.items[0].signalCategory)
        assertEquals(-2.01, result.items[0].onuRxDbm)
    }

    @Test
    fun `listConfigured trata q en blanco como null`() {
        every {
            onuRepository.findConfiguredFiltered(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, any()
            )
        } returns org.springframework.data.domain.Page.empty()

        service.listConfigured(page = 0, size = 50, filter = ConfiguredOnuFilter(q = "   "))

        verify(exactly = 1) {
            onuRepository.findConfiguredFiltered(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, any()
            )
        }
    }

    @Test
    fun `listConfigured mapea status pwrfail a lastDownCause`() {
        every {
            onuRepository.findConfiguredFiltered(
                q = null,
                board = null,
                port = null,
                oltId = null,
                zoneId = null,
                vlan = null,
                onuTypeId = null,
                onuTypeName = null,
                customProfile = null,
                ponType = null,
                mode = null,
                runState = null,
                signalCategory = null,
                splitterId = null,
                configurationMethod = null,
                wanMode = null,
                mgmtIpMode = null,
                importedSynced = null,
                lastResyncFailed = null,
                lineProfileMaptype = null,
                administrativeStatus = null,
                lastDownCause = "pwr",
                pageable = any()
            )
        } returns org.springframework.data.domain.Page.empty()

        service.listConfigured(page = 0, size = 50, filter = ConfiguredOnuFilter(status = "pwrfail"))

        verify(exactly = 1) {
            onuRepository.findConfiguredFiltered(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, "pwr", any()
            )
        }
    }

    @Test
    fun `getConfiguredByExternalId mapea campos de detalle desde BD`() {
        val zone = OltMgrZone(id = 10L, name = "Zone 1")
        val onu = OltMgrOnu(
            id = 1551L,
            sn = "ZTEGDC47DAD1",
            externalId = "gigafiber-ma5608t_1_1_25",
            olt = olt,
            board = 1,
            port = 1,
            onuIndex = 25,
            name = "WILI MORILLO",
            importedFromOlt = true,
            zone = zone,
            zoneName = "Zone 1",
            splitterId = 42L,
            splitterPort = 3,
            mode = "routing",
            mainVlanId = 100,
            ponType = "gpon",
            gponChannel = "gpon",
            customProfile = "Generic_1",
            wanMode = "onu_webpage",
            configurationMethod = "omci",
            mgmtIpMode = "inactive",
            mgmtVlanId = null,
            mgmtIpAddress = null,
            address = "Calle 1",
            contact = "999",
            lineProfileName = "line-1",
            serviceProfileName = "svc-1",
            authorizationDate = Instant.parse("2026-08-26T17:39:06Z")
        ).also {
            it.status = OltMgrOnuStatusCurrent(
                onu = it,
                runState = "online",
                matchState = "match",
                polledAt = Instant.parse("2026-08-27T12:00:00Z"),
                onuRxDbm = java.math.BigDecimal("-20.96"),
                oltRxDbm = java.math.BigDecimal("-25.53"),
                onuTxDbm = java.math.BigDecimal("2.14"),
                temperatureC = 37,
                distanceM = 795,
                lastDownCause = null,
                lastStatusChange = Instant.parse("2026-08-27T11:00:00Z")
            )
        }
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("gigafiber-ma5608t_1_1_25") } returns Optional.of(onu)

        val detail = service.getConfiguredByExternalId("gigafiber-ma5608t_1_1_25")

        assertNotNull(detail)
        assertEquals("ZTEGDC47DAD1", detail!!.sn)
        assertEquals("gigafiber-ma5608t_1_1_25", detail.externalId)
        assertEquals(1, detail.board)
        assertEquals(1, detail.port)
        assertEquals(25, detail.onuIndex)
        assertEquals("WILI MORILLO", detail.name)
        assertEquals("gigafiber-ma5608t", detail.oltName)
        assertEquals("Zone 1", detail.zoneName)
        assertEquals(42L, detail.splitterId)
        assertEquals(3, detail.splitterPort)
        assertEquals("routing", detail.mode)
        assertEquals(100, detail.vlan)
        assertEquals("gpon", detail.gponChannel)
        assertEquals("Generic_1", detail.customProfile)
        assertEquals("onu_webpage", detail.wanMode)
        assertEquals("omci", detail.configurationMethod)
        assertEquals("inactive", detail.mgmtIpMode)
        assertEquals(-20.96, detail.onuRxDbm)
        assertEquals(-25.53, detail.oltRxDbm)
        assertEquals(37.0, detail.temperatureC)
        assertEquals(795, detail.distanceM)
        assertEquals("online", detail.runState)
        assertEquals("line-1", detail.lineProfileName)
        assertEquals("svc-1", detail.serviceProfileName)
        assertEquals("Calle 1", detail.address)
        assertEquals("999", detail.contact)
    }

    @Test
    fun `getConfiguredByExternalId retorna null si no existe`() {
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("missing") } returns Optional.empty()

        assertNull(service.getConfiguredByExternalId("missing"))
    }

    @Test
    fun `getLiveStatusByExternalId combina onuDetail y optical`() {
        val onu = OltMgrOnu(
            id = 1551L,
            sn = "ZTEGDC47DAD1",
            externalId = "gigafiber-ma5608t_1_1_25",
            olt = olt,
            board = 1,
            port = 1,
            onuIndex = 25,
            name = "WILI MORILLO",
            importedFromOlt = true
        ).also {
            it.status = OltMgrOnuStatusCurrent(
                onu = it,
                runState = "offline",
                matchState = "mismatch",
                distanceM = 795
            )
        }
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("gigafiber-ma5608t_1_1_25") } returns Optional.of(onu)
        every { queryFacade.onuDetail(1, 1, 25) } returns com.dscorp.wispadmin.oltgateway.dto.OnuDetailDto(
            sn = "ZTEGDC47DAD1",
            frame = 0,
            slot = 1,
            port = 1,
            ontId = 25,
            description = "WILI MORILLO",
            runState = "online",
            controlFlag = "active",
            lineProfileId = 1,
            lineProfileName = "line-1",
            serviceProfileId = 2,
            serviceProfileName = "svc-1"
        )
        every { queryFacade.optical(1, 1, 25) } returns com.dscorp.wispadmin.oltgateway.dto.OpticalInfoDto(
            slot = 1,
            port = 1,
            ontId = 25,
            rxPowerDbm = -20.96,
            txPowerDbm = 2.14,
            temperatureC = 48.5,
            voltageV = 3.3,
            biasCurrentMa = 10.0,
            oltRxPowerDbm = -25.53
        )

        val live = service.getLiveStatusByExternalId("gigafiber-ma5608t_1_1_25")

        assertNotNull(live)
        assertEquals("ZTEGDC47DAD1", live!!.sn)
        assertEquals("online", live.runState)
        assertEquals("active", live.controlFlag)
        assertEquals("WILI MORILLO", live.description)
        assertEquals("mismatch", live.matchState)
        assertEquals(-20.96, live.onuRxDbm)
        assertEquals(2.14, live.onuTxDbm)
        assertEquals(-25.53, live.oltRxDbm)
        assertEquals(48.5, live.temperatureC)
        assertEquals(795, live.distanceM)
        assertEquals("line-1", live.lineProfileName)
        assertEquals("svc-1", live.serviceProfileName)
        verify(exactly = 1) { queryFacade.onuDetail(1, 1, 25) }
        verify(exactly = 1) { queryFacade.optical(1, 1, 25) }
    }

    @Test
    fun `getLiveStatusByExternalId prefiere match y distancia del optical SNMP`() {
        val onu = OltMgrOnu(
            id = 1551L,
            sn = "ZTEGDC47DAD1",
            externalId = "gigafiber-ma5608t_1_1_25",
            olt = olt,
            board = 1,
            port = 1,
            onuIndex = 25,
            importedFromOlt = true
        ).also {
            it.status = OltMgrOnuStatusCurrent(
                onu = it,
                runState = "offline",
                matchState = "mismatch",
                distanceM = 100
            )
        }
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("gigafiber-ma5608t_1_1_25") } returns Optional.of(onu)
        every { queryFacade.onuDetail(1, 1, 25) } returns com.dscorp.wispadmin.oltgateway.dto.OnuDetailDto(
            sn = "ZTEGDC47DAD1",
            frame = 0,
            slot = 1,
            port = 1,
            ontId = 25,
            description = null,
            runState = "online",
            controlFlag = null,
            lineProfileId = null,
            lineProfileName = null,
            serviceProfileId = null,
            serviceProfileName = null
        )
        every { queryFacade.optical(1, 1, 25) } returns com.dscorp.wispadmin.oltgateway.dto.OpticalInfoDto(
            slot = 1,
            port = 1,
            ontId = 25,
            rxPowerDbm = -21.0,
            txPowerDbm = 2.0,
            temperatureC = 49.0,
            voltageV = null,
            biasCurrentMa = 11.0,
            oltRxPowerDbm = -25.0,
            distanceM = 795,
            matchState = "match"
        )

        val live = service.getLiveStatusByExternalId("gigafiber-ma5608t_1_1_25")

        assertNotNull(live)
        assertEquals("match", live!!.matchState)
        assertEquals(795, live.distanceM)
        assertEquals(49.0, live.temperatureC)
        assertEquals(11.0, live.biasCurrentMa)
    }

    @Test
    fun `getLiveStatusByExternalId persiste temperatura y distancia en status`() {
        val onu = OltMgrOnu(
            id = 1551L,
            sn = "ZTEGDC47DAD1",
            externalId = "gigafiber-ma5608t_1_1_25",
            olt = olt,
            board = 1,
            port = 1,
            onuIndex = 25,
            importedFromOlt = true
        ).also {
            it.status = OltMgrOnuStatusCurrent(
                onu = it,
                runState = "offline",
                matchState = null,
                distanceM = null,
                temperatureC = null
            )
        }
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("gigafiber-ma5608t_1_1_25") } returns Optional.of(onu)
        every { queryFacade.onuDetail(1, 1, 25) } returns com.dscorp.wispadmin.oltgateway.dto.OnuDetailDto(
            sn = "ZTEGDC47DAD1",
            frame = 0,
            slot = 1,
            port = 1,
            ontId = 25,
            description = null,
            runState = "online",
            controlFlag = null,
            lineProfileId = null,
            lineProfileName = null,
            serviceProfileId = null,
            serviceProfileName = null
        )
        every { queryFacade.optical(1, 1, 25) } returns com.dscorp.wispadmin.oltgateway.dto.OpticalInfoDto(
            slot = 1,
            port = 1,
            ontId = 25,
            rxPowerDbm = -21.0,
            txPowerDbm = 2.0,
            temperatureC = 37.0,
            voltageV = null,
            biasCurrentMa = 11.0,
            oltRxPowerDbm = -25.0,
            distanceM = 795,
            matchState = "match"
        )
        every { statusRepository.save(any()) } answers { firstArg() }

        service.getLiveStatusByExternalId("gigafiber-ma5608t_1_1_25")

        assertEquals(37, onu.status?.temperatureC)
        assertEquals(795, onu.status?.distanceM)
        assertEquals("match", onu.status?.matchState)
        verify(exactly = 1) { statusRepository.save(onu.status!!) }
    }

    @Test
    fun `getLiveStatusByExternalId retorna null si no existe`() {
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("missing") } returns Optional.empty()

        assertNull(service.getLiveStatusByExternalId("missing"))
    }

    @Test
    fun `getHistoryByExternalId mapea audit logs de la ONU`() {
        val onu = OltMgrOnu(
            id = 1551L,
            sn = "ZTEGDC47DAD1",
            externalId = "gigafiber-ma5608t_1_1_25",
            olt = olt,
            board = 1,
            port = 1,
            onuIndex = 25,
            name = "WILI MORILLO",
            importedFromOlt = true
        )
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("gigafiber-ma5608t_1_1_25") } returns Optional.of(onu)
        every {
            auditLogRepository.findByOnu_IdOrderByCreatedAtDesc(1551L, any())
        } returns listOf(
            OltMgrAuditLog(
                id = 9L,
                olt = olt,
                onu = onu,
                action = "reboot_onu",
                userId = 1L,
                source = "api",
                ipAddress = "10.0.0.1",
                details = """{"sn":"ZTEGDC47DAD1"}""",
                createdAt = Instant.parse("2026-08-27T15:00:00Z")
            )
        )

        val history = service.getHistoryByExternalId("gigafiber-ma5608t_1_1_25", limit = 20)

        assertNotNull(history)
        assertEquals(1, history!!.items.size)
        assertEquals(9L, history.items[0].id)
        assertEquals("reboot_onu", history.items[0].action)
        assertEquals(1L, history.items[0].userId)
        assertEquals("10.0.0.1", history.items[0].ipAddress)
        assertEquals("""{"sn":"ZTEGDC47DAD1"}""", history.items[0].details)
        assertEquals("2026-08-27T15:00:00Z", history.items[0].createdAt)
    }

    @Test
    fun `getHistoryByExternalId retorna null si no existe`() {
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("missing") } returns Optional.empty()

        assertNull(service.getHistoryByExternalId("missing"))
    }

    @Test
    fun `insert persiste distancia lastDown y perfiles SNMP`() {
        every { queryFacade.listOnusParsed() } returns listOf(
            summary(
                sn = "SNEXTRAS01",
                slot = 1,
                port = 1,
                ontId = 25,
                runState = "online",
                matchState = "match",
                description = "WILI",
                distanceM = 795,
                lastDownCause = "los",
                lineProfileName = "line-1",
                serviceProfileName = "svc-1"
            )
        )
        val saved = slot<Iterable<OltMgrOnu>>()
        every { onuRepository.saveAll(capture(saved)) } answers { firstArg<Iterable<OltMgrOnu>>().toList() }

        val result = service.syncInventory()

        assertEquals(1, result.inserted)
        val savedOnu = saved.captured.single()
        assertEquals(795, savedOnu.status?.distanceM)
        assertEquals("los", savedOnu.status?.lastDownCause)
        assertEquals("line-1", savedOnu.lineProfileName)
        assertEquals("svc-1", savedOnu.serviceProfileName)
    }

    private fun summary(
        sn: String,
        slot: Int,
        port: Int,
        ontId: Int,
        runState: String? = "online",
        matchState: String? = "match",
        description: String? = null,
        distanceM: Int? = null,
        lastDownCause: String? = null,
        lineProfileName: String? = null,
        serviceProfileName: String? = null
    ) = ParsedOnuSummary(
        frame = 0,
        slot = slot,
        port = port,
        ontId = ontId,
        sn = sn,
        controlFlag = "active",
        runState = runState,
        configState = "normal",
        matchState = matchState,
        description = description,
        distanceM = distanceM,
        lastDownCause = lastDownCause,
        lineProfileName = lineProfileName,
        serviceProfileName = serviceProfileName
    )
}
