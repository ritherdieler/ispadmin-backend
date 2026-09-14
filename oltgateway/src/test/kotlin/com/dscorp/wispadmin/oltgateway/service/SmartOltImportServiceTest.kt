package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuType
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuTypeRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrZoneRepository
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltAllOnusPageDto
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltCatalogClient
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltConfiguredOnuDto
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltOnuTypesResponseDto
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltZonesResponseDto
import com.dscorp.wispadmin.oltgateway.snmp.HuaweiGponSnmpCodec
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class SmartOltImportServiceTest {

    private val catalogClient = mockk<SmartOltCatalogClient>()
    private val oltRepository = mockk<OltMgrOltRepository>()
    private val zoneRepository = mockk<OltMgrZoneRepository>()
    private val onuTypeRepository = mockk<OltMgrOnuTypeRepository>()
    private val onuRepository = mockk<OltMgrOnuRepository>()
    private val statusRepository = mockk<OltMgrOnuStatusCurrentRepository>()
    private val auditLogRepository = mockk<OltMgrAuditLogRepository>()
    private val properties = OltGatewayProperties().apply { oltId = "gigafiber-ma5608t" }
    private val mapper = ObjectMapper()
    private val idSeq = AtomicLong(1)

    private lateinit var service: SmartOltImportService
    private val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")

    @BeforeEach
    fun setUp() {
        service = SmartOltImportService(
            catalogClient = catalogClient,
            oltRepository = oltRepository,
            zoneRepository = zoneRepository,
            onuTypeRepository = onuTypeRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            auditLogRepository = auditLogRepository,
            properties = properties
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { auditLogRepository.save(any()) } answers {
            firstArg<OltMgrAuditLog>().also { if (it.id == null) it.id = idSeq.getAndIncrement() }
        }
    }

    @Test
    fun `importFromSmartOlt inserta ONUs nuevas e importa catalogos`() {
        val zones = loadZones()
        val types = loadTypes()
        val page = loadPage()
        every { catalogClient.fetchZones() } returns zones
        every { catalogClient.fetchOnuTypes() } returns types
        every { catalogClient.fetchAllOnusDetails(1, 100) } returns page
        every { zoneRepository.findByName(any()) } returns Optional.empty()
        every { zoneRepository.save(any()) } answers {
            firstArg<OltMgrZone>().also { if (it.id == null) it.id = idSeq.getAndIncrement() }
        }
        every { onuTypeRepository.findByName(any()) } returns Optional.empty()
        every { onuTypeRepository.save(any()) } answers {
            firstArg<OltMgrOnuType>().also { if (it.id == null) it.id = idSeq.getAndIncrement() }
        }
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(any()) } returns Optional.empty()
        every { onuRepository.findBySn(any()) } returns Optional.empty()
        val onuSlot = slot<OltMgrOnu>()
        every { onuRepository.save(capture(onuSlot)) } answers {
            firstArg<OltMgrOnu>().also { if (it.id == null) it.id = idSeq.getAndIncrement() }
        }
        every { statusRepository.save(any()) } answers { firstArg() }

        val result = service.importFromSmartOlt(pageSize = 100)

        assertNull(result.error)
        assertEquals(2, result.zonesImported)
        assertEquals(1, result.onuTypesImported)
        assertEquals(2, result.inserted)
        assertEquals(0, result.updated)
        assertEquals(2, result.totalItems)
        assertEquals(1, result.pagesFetched)
        verify(exactly = 2) { onuRepository.save(any()) }
        verify(exactly = 1) { auditLogRepository.save(match { it.action == "smartolt_import" }) }
    }

    @Test
    fun `importFromSmartOlt enriquece ONU existente de sync SNMP sin pisar posicion`() {
        val zones = loadZones()
        val types = loadTypes()
        val page = loadPage()
        val zone = OltMgrZone(id = 10L, name = "Zone 1")
        val type = OltMgrOnuType(id = 20L, name = "HG8245H")
        val normalizedExistingSn = HuaweiGponSnmpCodec.normalizeOntSn("4857544311E70E9A")
        val existing = OltMgrOnu(
            id = 99L,
            sn = normalizedExistingSn,
            externalId = "gigafiber-ma5608t_1_0_5",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 5
        )
        existing.status = OltMgrOnuStatusCurrent(
            onu = existing,
            runState = "online",
            signalCategory = "good"
        )
        every { catalogClient.fetchZones() } returns zones
        every { catalogClient.fetchOnuTypes() } returns types
        every { catalogClient.fetchAllOnusDetails(1, 100) } returns page
        every { zoneRepository.findByName("Zone 1") } returns Optional.of(zone)
        every { zoneRepository.findByName("Zone 2") } returns Optional.empty()
        every { zoneRepository.save(any()) } answers {
            firstArg<OltMgrZone>().also { if (it.id == null) it.id = idSeq.getAndIncrement() }
        }
        every { onuTypeRepository.findByName("HG8245H") } returns Optional.of(type)
        every { onuTypeRepository.save(any()) } answers { firstArg() }
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(normalizedExistingSn) } returns Optional.of(existing)
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull("VSOL0086F6E9") } returns Optional.empty()
        every { onuRepository.findBySn("VSOL0086F6E9") } returns Optional.empty()
        every { onuRepository.save(any()) } answers { firstArg() }
        every { statusRepository.save(any()) } answers { firstArg() }

        val result = service.importFromSmartOlt(pageSize = 100)

        assertEquals(1, result.updated)
        assertEquals(1, result.inserted)
        assertEquals("Zone 1", existing.zoneName)
        assertEquals(100, existing.mainVlanId)
        assertEquals("Generic_1", existing.customProfile)
        assertEquals(1, existing.board)
        assertEquals(0, existing.port)
        assertTrue(existing.importedFromOlt)
    }

    @Test
    fun `importFromSmartOlt recorre todas las paginas usando total_pages solo de la primera`() {
        val onuItem = SmartOltConfiguredOnuDto(
            uniqueExternalId = "gigafiber-ma5608t_1_0_5",
            sn = "4857544311E70E9A",
            oltName = "gigafiber-ma5608t",
            board = "1",
            port = "0",
            onu = "5",
            zoneName = "Zone 1",
            onuTypeName = "HG8245H",
            vlan = "100"
        )
        every { catalogClient.fetchZones() } returns SmartOltZonesResponseDto()
        every { catalogClient.fetchOnuTypes() } returns SmartOltOnuTypesResponseDto()
        every { catalogClient.fetchAllOnusDetails(1, 100) } returns SmartOltAllOnusPageDto(
            page = 1,
            pageSize = 100,
            totalItems = 3,
            totalPages = 3,
            onus = listOf(onuItem.copy(sn = "4857544311E70E9A"))
        )
        every { catalogClient.fetchAllOnusDetails(2, 100) } returns SmartOltAllOnusPageDto(
            page = 2,
            pageSize = 100,
            totalPages = 0,
            onus = listOf(onuItem.copy(sn = "4857544311E70E9B"))
        )
        every { catalogClient.fetchAllOnusDetails(3, 100) } returns SmartOltAllOnusPageDto(
            page = 3,
            pageSize = 100,
            totalPages = 0,
            onus = listOf(onuItem.copy(sn = "4857544311E70E9C"))
        )
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(any()) } returns Optional.empty()
        every { onuRepository.findBySn(any()) } returns Optional.empty()
        every { zoneRepository.findByName(any()) } returns Optional.empty()
        every { onuTypeRepository.findByName(any()) } returns Optional.empty()
        every { onuRepository.save(any()) } answers {
            firstArg<OltMgrOnu>().also { if (it.id == null) it.id = idSeq.getAndIncrement() }
        }
        every { statusRepository.save(any()) } answers { firstArg() }

        val result = service.importFromSmartOlt(pageSize = 100)

        assertEquals(3, result.pagesFetched)
        assertEquals(3, result.totalItems)
        assertEquals(3, result.inserted)
        verify(exactly = 3) { catalogClient.fetchAllOnusDetails(any(), 100) }
    }

    private fun loadZones(): SmartOltZonesResponseDto = load("smartolt/get_zones.json", SmartOltZonesResponseDto::class.java)

    private fun loadTypes(): SmartOltOnuTypesResponseDto =
        load("smartolt/get_onu_types.json", SmartOltOnuTypesResponseDto::class.java)

    private fun loadPage(): SmartOltAllOnusPageDto =
        load("smartolt/get_all_onus_details_page1.json", SmartOltAllOnusPageDto::class.java)

    private fun <T> load(path: String, type: Class<T>): T {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: error("Missing fixture $path")
        return mapper.readValue(stream, type)
    }
}
