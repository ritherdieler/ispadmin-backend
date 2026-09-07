package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.MoveOnuFormDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.config.OnuWriteProvider
import com.dscorp.wispadmin.oltgateway.config.OnuWriteProviderProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuType
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrTask
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuTypeRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrZoneRepository
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import com.dscorp.wispadmin.oltgateway.mapper.SmartOltCompatMapper
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuBySn
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltWriteClient
import com.dscorp.wispadmin.oltgateway.smartolt.SmartOltWriteResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

class OltManagerFacadeTest {

    private val oltRepository = mockk<OltMgrOltRepository>()
    private val zoneRepository = mockk<OltMgrZoneRepository>(relaxed = true)
    private val onuTypeRepository = mockk<OltMgrOnuTypeRepository>(relaxed = true)
    private val onuRepository = mockk<OltMgrOnuRepository>()
    private val statusRepository = mockk<OltMgrOnuStatusCurrentRepository>(relaxed = true)
    private val taskRepository = mockk<OltMgrTaskRepository>()
    private val auditLogRepository = mockk<OltMgrAuditLogRepository>()
    private val commandService = mockk<OltGatewayCommandService>()
    private val smartOltWriteClient = mockk<SmartOltWriteClient>(relaxed = true)
    private val writeProviderProperties = OnuWriteProviderProperties()
    private val queryFacade = mockk<OltGatewayQueryFacade>()
    private val mapper = SmartOltCompatMapper()
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        writes.enabled = true
        writes.defaultLineProfileId = 10
        writes.defaultServiceProfileId = 10
    }

    private lateinit var facade: OltManagerFacade

    private val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")

    @BeforeEach
    fun setUp() {
        writeProviderProperties.authorize = OnuWriteProvider.GATEWAY
        writeProviderProperties.delete = OnuWriteProvider.GATEWAY
        writeProviderProperties.reboot = OnuWriteProvider.GATEWAY
        writeProviderProperties.move = OnuWriteProvider.GATEWAY
        facade = OltManagerFacade(
            oltRepository = oltRepository,
            zoneRepository = zoneRepository,
            onuTypeRepository = onuTypeRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            taskRepository = taskRepository,
            auditLogRepository = auditLogRepository,
            commandService = commandService,
            writeRouter = OnuWriteRouter(writeProviderProperties, commandService, smartOltWriteClient),
            queryFacade = queryFacade,
            mapper = mapper,
            properties = properties
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { taskRepository.save(any()) } answers { firstArg<OltMgrTask>().also { if (it.id == null) it.id = 99L } }
        every { auditLogRepository.save(any()) } answers { firstArg<OltMgrAuditLog>().also { if (it.id == null) it.id = 88L } }
        every { statusRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `unconfiguredOnus delega a CLI capa C`() {
        every { queryFacade.autofindParsed() } returns listOf(
            ParsedAutofindOnt(frame = 0, slot = 0, port = 2, sn = "4857544311E70E9A", equipmentId = "HG8245H")
        )
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(any()) } returns Optional.empty()

        val response = facade.unconfiguredOnus()

        assertTrue(response.status)
        assertEquals("4857544311E70E9A", response.response[0].sn)
        assertEquals("0", response.response[0].board)
    }

    @Test
    fun `unconfiguredOnus excluye SN ya autorizados en inventario`() {
        every { queryFacade.autofindParsed() } returns listOf(
            ParsedAutofindOnt(frame = 0, slot = 1, port = 6, sn = "HWTCC6FBA6AA"),
            ParsedAutofindOnt(frame = 0, slot = 1, port = 0, sn = "HWTC0086CD49")
        )
        val existing = OltMgrOnu(
            id = 7L,
            sn = "HWTC0086CD49",
            externalId = "gigafiber-ma5608t_1_0_1",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 1
        )
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull("HWTCC6FBA6AA") } returns Optional.empty()
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull("HWTC0086CD49") } returns Optional.of(existing)

        val response = facade.unconfiguredOnus()

        assertEquals(1, response.response.size)
        assertEquals("HWTCC6FBA6AA", response.response[0].sn)
    }

    @Test
    fun `getOnusDetailsBySn usa A+B cuando existe en DB`() {
        val onu = OltMgrOnu(
            id = 10L,
            sn = "4857544311E70E9A",
            externalId = "gigafiber-ma5608t_1_0_5",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 5,
            name = "cliente_db",
            mainVlanId = 100,
            onuTypeName = "HG8245H",
            zoneName = "ZonaA"
        )
        onu.status = OltMgrOnuStatusCurrent(onu = onu, onuId = 10L, runState = "online")
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.of(onu)

        val response = facade.getOnusDetailsBySn("4857544311E70E9A")

        assertTrue(response.status)
        assertEquals("cliente_db", response.onus[0].name)
        assertEquals("online", response.onus[0].administrative_status)
        assertEquals("gigafiber-ma5608t_1_0_5", response.onus[0].unique_external_id)
        verify(exactly = 0) { queryFacade.bySnParsed(any()) }
    }

    @Test
    fun `getOnusDetailsBySn resuelve por posicion cuando SN en DB es hex y consulta es vendor`() {
        val onu = OltMgrOnu(
            id = 10L,
            sn = "5A544547DC47DF15",
            externalId = "gigafiber-ma5608t_1_7_1",
            olt = olt,
            board = 1,
            port = 7,
            onuIndex = 1,
            name = "WALTHER MIGUEL NICHO",
            importedFromOlt = true
        )
        onu.status = OltMgrOnuStatusCurrent(onu = onu, onuId = 10L, runState = "online")
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull("ZTEGDC47DF15") } returns Optional.empty()
        every { queryFacade.bySnParsed("ZTEGDC47DF15") } returns ParsedOnuBySn(
            frame = 0,
            slot = 1,
            port = 7,
            ontId = 1,
            sn = "ZTEGDC47DF15",
            description = "WALTHER MIGUEL NICHO",
            runState = "online",
            controlFlag = "active",
            lineProfileId = 3,
            lineProfileName = "Generic_1_V1",
            serviceProfileId = 2,
            serviceProfileName = "Generic_1_V1"
        )
        every {
            onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(1L, 1, 7, 1)
        } returns Optional.of(onu)

        val response = facade.getOnusDetailsBySn("ZTEGDC47DF15")

        assertTrue(response.status)
        assertEquals("WALTHER MIGUEL NICHO", response.onus[0].name)
        assertEquals("gigafiber-ma5608t_1_7_1", response.onus[0].unique_external_id)
        verify(exactly = 0) { onuRepository.save(any()) }
        verify(exactly = 0) { statusRepository.save(any()) }
    }

    @Test
    fun `getOnusDetailsBySn hidrata desde C si no hay A`() {
        every { onuRepository.findBySnIgnoreCaseAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.empty()
        every { queryFacade.bySnParsed("4857544311E70E9A") } returns ParsedOnuBySn(
            frame = 0,
            slot = 1,
            port = 0,
            ontId = 5,
            sn = "4857544311E70E9A",
            description = "from_cli",
            runState = "online",
            controlFlag = "active",
            lineProfileId = 10,
            lineProfileName = "line-profile_10",
            serviceProfileId = 10,
            serviceProfileName = "srv-profile_10"
        )
        every {
            onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(1L, 1, 0, 5)
        } returns Optional.empty()
        every { onuRepository.save(any()) } answers {
            firstArg<OltMgrOnu>().also { it.id = 11L }
        }

        val response = facade.getOnusDetailsBySn("4857544311E70E9A")

        assertTrue(response.status)
        assertEquals("from_cli", response.onus[0].name)
        verify { onuRepository.save(any()) }
        verify { statusRepository.save(match { it.onu.id == 11L && it.runState == "online" }) }
    }

    @Test
    fun `authorizeOnu crea task CLI insert A y audit`() {
        every { onuRepository.findBySnAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.empty()
        every { onuRepository.findBySn("4857544311E70E9A") } returns Optional.empty()
        every { zoneRepository.findByName("ZonaA") } returns Optional.of(OltMgrZone(id = 2L, name = "ZonaA"))
        every { onuTypeRepository.findByName("HG8245H") } returns Optional.of(OltMgrOnuType(id = 3L, name = "HG8245H"))
        val savedOnu = slot<OltMgrOnu>()
        every { onuRepository.save(capture(savedOnu)) } answers {
            firstArg<OltMgrOnu>().also { it.id = 20L }
        }
        every { onuRepository.findMaxOnuIndex(1L, 0, 2) } returns 6
        val cli = slot<AuthorizeCliRequest>()
        every { commandService.authorize(capture(cli)) } returns AuthorizeCliResult(ontId = 7, commands = emptyList())

        val response = facade.authorizeOnu(
            AuthorizeOnuFormDto(
                olt_id = "gigafiber-ma5608t",
                board = "0",
                port = "2",
                sn = "4857544311E70E9A",
                vlan = "100",
                onu_type = "HG8245H",
                zone = "ZonaA",
                name = "nuevo",
                onu_mode = "routing",
                custom_profile = "Generic_1"
            )
        )

        assertTrue(response.status)
        assertEquals("gigafiber-ma5608t_0_2_7", response.unique_external_id)
        assertEquals(7, savedOnu.captured.onuIndex)
        assertEquals(olt, savedOnu.captured.olt)
        assertEquals(2L, savedOnu.captured.zone?.id)
        assertEquals(3L, savedOnu.captured.onuType?.id)
        assertEquals(6, cli.captured.lineProfileId)
        assertEquals(13, cli.captured.serviceProfileId)
        assertTrue(cli.captured.description.contains("nuevo_zone_ZonaA_authd_"))
        verify { commandService.authorize(any()) }
        verify { taskRepository.save(match { it.type == "authorize" && it.status == "success" }) }
        verify { auditLogRepository.save(match { it.action == "authorize_onu" && it.onu?.id == 20L }) }
    }

    @Test
    fun `authorizeOnu SMARTOLT persiste unique_external_id cloud sin SSH`() {
        writeProviderProperties.authorize = OnuWriteProvider.SMARTOLT
        every { onuRepository.findBySnAndDeletedAtIsNull("ZTEGDC47BFFD") } returns Optional.empty()
        every { onuRepository.findBySn("ZTEGDC47BFFD") } returns Optional.empty()
        every { zoneRepository.findByName("Zone 1") } returns Optional.of(OltMgrZone(id = 2L, name = "Zone 1"))
        every { onuTypeRepository.findByName("F6600RV9.0.21") } returns Optional.of(OltMgrOnuType(id = 3L, name = "F6600RV9.0.21"))
        every { smartOltWriteClient.authorize(any()) } returns SmartOltWriteResult(true, "cloud_1_6_16")
        val savedOnu = slot<OltMgrOnu>()
        every { onuRepository.save(capture(savedOnu)) } answers {
            firstArg<OltMgrOnu>().also { it.id = 20L }
        }
        every { onuRepository.findMaxOnuIndex(1L, 1, 6) } returns 15

        val response = facade.authorizeOnu(
            AuthorizeOnuFormDto(
                olt_id = "gigafiber-ma5608t",
                board = "1",
                port = "6",
                sn = "ZTEGDC47BFFD",
                vlan = "100",
                onu_type = "F6600RV9.0.21",
                zone = "Zone 1",
                name = "lab",
                onu_mode = "Routing",
                custom_profile = "Generic_1"
            )
        )

        assertTrue(response.status)
        assertEquals("cloud_1_6_16", response.unique_external_id)
        assertEquals("cloud_1_6_16", savedOnu.captured.externalId)
        verify(exactly = 1) { smartOltWriteClient.authorize(any()) }
        verify(exactly = 0) { commandService.authorize(any()) }
    }

    @Test
    fun `moveOnu actualiza A tras CLI`() {
        val onu = OltMgrOnu(
            id = 10L,
            sn = "4857544311E70E9A",
            externalId = "gigafiber-ma5608t_0_2_5",
            olt = olt,
            board = 0,
            port = 2,
            onuIndex = 5,
            name = "cliente",
            mainVlanId = 100,
            lineProfileName = "line-profile_10",
            serviceProfileName = "srv-profile_10"
        )
        every { onuRepository.findBySnAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.of(onu)
        every { commandService.move(any()) } returns Unit
        every { onuRepository.save(any()) } answers { firstArg() }

        val response = facade.moveOnu("4857544311E70E9A", MoveOnuFormDto(olt_id = "gigafiber-ma5608t", board = "1", port = "0"))

        assertTrue(response.status)
        assertEquals(1, onu.board)
        assertEquals(0, onu.port)
        verify { commandService.move(any()) }
        verify { auditLogRepository.save(match { it.action == "move_onu" }) }
    }

    @Test
    fun `moveOnu conserva el identificador externo porque es propio y estable`() {
        val onu = OltMgrOnu(
            id = 10L,
            sn = "4857544311E70E9A",
            externalId = "gigafiber-ma5608t_0_2_5",
            olt = olt,
            board = 0,
            port = 2,
            onuIndex = 5,
            name = "cliente",
            mainVlanId = 100
        )
        every { onuRepository.findBySnAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.of(onu)
        every { commandService.move(any()) } returns Unit
        every { onuRepository.save(any()) } answers { firstArg() }

        val response = facade.moveOnu(
            "4857544311E70E9A",
            MoveOnuFormDto(olt_id = "gigafiber-ma5608t", board = "1", port = "0")
        )

        assertEquals("gigafiber-ma5608t_0_2_5", onu.externalId)
        assertEquals("gigafiber-ma5608t_0_2_5", response.unique_external_id)
    }

    @Test
    fun `planAuthorize calcula posicion e identificador sin tocar la OLT`() {
        every { onuRepository.findBySnAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.empty()
        every { onuRepository.findMaxOnuIndex(1L, 0, 2) } returns 6
        every { commandService.planAuthorize(any()) } returns listOf("interface gpon 0/0", "ont add 2 7 sn-auth 4857544311E70E9A")

        val plan = facade.planAuthorize(
            AuthorizeOnuFormDto(
                olt_id = "gigafiber-ma5608t",
                board = "0",
                port = "2",
                sn = "4857544311E70E9A",
                vlan = "100",
                name = "nuevo"
            )
        )

        assertEquals(7, plan.ontId)
        assertEquals("gigafiber-ma5608t_0_2_7", plan.externalId)
        assertFalse(plan.alreadyAuthorized)
        assertEquals(2, plan.commands.size)
        verify(exactly = 0) { commandService.authorize(any()) }
        verify(exactly = 0) { onuRepository.save(any()) }
        verify(exactly = 0) { taskRepository.save(any()) }
    }

    @Test
    fun `recordAuthorizeShadow registra la divergencia de posicion sin aplicar nada`() {
        every { onuRepository.findBySnAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.empty()
        every { onuRepository.findMaxOnuIndex(1L, 0, 2) } returns 6
        every { commandService.planAuthorize(any()) } returns listOf("ont add 2 7 sn-auth 4857544311E70E9A")

        val divergence = facade.recordAuthorizeShadow(
            AuthorizeOnuFormDto(
                olt_id = "gigafiber-ma5608t",
                board = "0",
                port = "2",
                sn = "4857544311E70E9A",
                vlan = "100",
                name = "nuevo"
            ),
            applied = AppliedAuthorization(board = 0, port = 2, ontId = 9, externalId = "184")
        )

        assertTrue(divergence)
        verify {
            auditLogRepository.save(
                match { it.action == "authorize_shadow" && it.onu == null && it.details!!.contains("\"diverges\":true") }
            )
        }
        verify(exactly = 0) { commandService.authorize(any()) }
        verify(exactly = 0) { onuRepository.save(any()) }
    }

    @Test
    fun `recordAuthorizeShadow no reporta divergencia cuando la posicion coincide`() {
        every { onuRepository.findBySnAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.empty()
        every { onuRepository.findMaxOnuIndex(1L, 0, 2) } returns 6
        every { commandService.planAuthorize(any()) } returns emptyList()

        val divergence = facade.recordAuthorizeShadow(
            AuthorizeOnuFormDto(
                olt_id = "gigafiber-ma5608t",
                board = "0",
                port = "2",
                sn = "4857544311E70E9A",
                vlan = "100",
                name = "nuevo"
            ),
            applied = AppliedAuthorization(board = 0, port = 2, ontId = 7, externalId = "184")
        )

        assertFalse(divergence)
        verify { auditLogRepository.save(match { it.action == "authorize_shadow" }) }
    }

    @Test
    fun `recordAuthorizeShadow marca divergencia cuando no se pudo leer lo aplicado`() {
        every { onuRepository.findBySnAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.empty()
        every { onuRepository.findMaxOnuIndex(1L, 0, 2) } returns 6
        every { commandService.planAuthorize(any()) } returns emptyList()

        val divergence = facade.recordAuthorizeShadow(
            AuthorizeOnuFormDto(board = "0", port = "2", sn = "4857544311E70E9A"),
            applied = null
        )

        assertTrue(divergence)
    }

    @Test
    fun `recordAuthorizeShadow nunca propaga un fallo del plan`() {
        every { onuRepository.findBySnAndDeletedAtIsNull("4857544311E70E9A") } returns Optional.empty()
        every { onuRepository.findMaxOnuIndex(any(), any(), any()) } throws IllegalStateException("db caida")

        val divergence = facade.recordAuthorizeShadow(
            AuthorizeOnuFormDto(board = "0", port = "2", sn = "4857544311E70E9A"),
            applied = AppliedAuthorization(board = 0, port = 2, ontId = 9, externalId = "184")
        )

        assertFalse(divergence)
    }

    @Test
    fun `deleteOnu soft-delete A`() {
        val onu = OltMgrOnu(
            id = 10L,
            sn = "4857544311E70E9A",
            externalId = "gigafiber-ma5608t_1_0_5",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 5
        )
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("gigafiber-ma5608t_1_0_5") } returns Optional.of(onu)
        every { commandService.delete(any()) } returns Unit
        every { onuRepository.save(any()) } answers { firstArg() }

        val response = facade.deleteOnu("gigafiber-ma5608t_1_0_5")

        assertTrue(response.status)
        assertTrue(onu.deletedAt != null)
        assertTrue(onu.sn.contains("#del#"))
        assertEquals(-1, onu.board)
        assertEquals(0, onu.port)
        assertEquals(10, onu.onuIndex)
        assertEquals("gigafiber-ma5608t_deleted_10", onu.externalId)
        verify { commandService.delete(any()) }
        verify { auditLogRepository.save(match { it.action == "delete_onu" }) }
    }

    @Test
    fun `authorizeOnu frees soft-deleted sn before insert`() {
        val softDeleted = OltMgrOnu(
            id = 44L,
            sn = "ZTEGDC47BFFD",
            externalId = "gigafiber-ma5608t_1_6_16",
            olt = olt,
            board = 1,
            port = 6,
            onuIndex = 16,
            deletedAt = java.time.Instant.parse("2026-09-04T00:00:00Z"),
        )
        every { onuRepository.findBySnAndDeletedAtIsNull("ZTEGDC47BFFD") } returns Optional.empty()
        every { onuRepository.findBySn("ZTEGDC47BFFD") } returns Optional.of(softDeleted)
        every { onuRepository.saveAndFlush(any()) } answers { firstArg() }
        every { onuRepository.findMaxOnuIndex(1L, 1, 6) } returns 16
        every { zoneRepository.findByName("Zone 1") } returns Optional.empty()
        every { zoneRepository.save(any()) } answers { firstArg<OltMgrZone>().also { it.id = 9L } }
        every { onuTypeRepository.findByName("F6600RV9.0.21") } returns Optional.empty()
        every { onuTypeRepository.save(any()) } answers { firstArg<OltMgrOnuType>().also { it.id = 8L } }
        every { commandService.authorize(any()) } returns AuthorizeCliResult(ontId = 17, commands = emptyList())
        every { onuRepository.save(any()) } answers { firstArg<OltMgrOnu>().also { if (it.id == null) it.id = 55L } }

        val response = facade.authorizeOnu(
            AuthorizeOnuFormDto(
                olt_id = "2",
                pon_type = "gpon",
                board = "1",
                port = "6",
                sn = "ZTEGDC47BFFD",
                vlan = "100",
                onu_type = "F6600RV9.0.21",
                zone = "Zone 1",
                name = "Lab",
                onu_mode = "Routing",
                custom_profile = "Generic_1",
            )
        )

        assertTrue(response.status)
        assertTrue(softDeleted.sn.startsWith("ZTEGDC47BFFD#del#"))
        verify { onuRepository.saveAndFlush(softDeleted) }
        verify { commandService.authorize(any()) }
    }

    @Test
    fun `rebootOnu crea task y CLI sin tocar A`() {
        val onu = OltMgrOnu(
            id = 10L,
            sn = "4857544311E70E9A",
            externalId = "gigafiber-ma5608t_1_0_5",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 5
        )
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("gigafiber-ma5608t_1_0_5") } returns Optional.of(onu)
        every { commandService.reboot(any()) } returns Unit

        val response = facade.rebootOnu("gigafiber-ma5608t_1_0_5")

        assertTrue(response.status)
        verify { commandService.reboot(any()) }
        verify { taskRepository.save(match { it.type == "reboot" && it.status == "success" }) }
        verify(exactly = 0) { onuRepository.save(any()) }
    }

    @Test
    fun `rebootOnu 404 si no existe`() {
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("missing") } returns Optional.empty()

        assertThrows<OnuNotFoundException> { facade.rebootOnu("missing") }
    }
}
