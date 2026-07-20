package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSyncRun
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrSyncRunRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.exception.CliBusBusyException
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
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
    private val cliBus = mockk<OltCliBus>()
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        sync.skipWhenWriteRunning = true
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
            cliBus = cliBus
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
        every { auditLogRepository.saveAll(any<Iterable<OltMgrAuditLog>>()) } answers {
            firstArg<Iterable<OltMgrAuditLog>>().toList()
        }
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

    private fun summary(
        sn: String,
        slot: Int,
        port: Int,
        ontId: Int,
        runState: String? = "online",
        matchState: String? = "match",
        description: String? = null
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
        description = description
    )
}
