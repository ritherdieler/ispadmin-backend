package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.MoveOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.UpdateWanFormDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
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
import com.dscorp.wispadmin.oltgateway.exception.OltGatewayValidationException
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import com.dscorp.wispadmin.oltgateway.mapper.SmartOltCompatMapper
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuBySn
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        facade = OltManagerFacade(
            oltRepository = oltRepository,
            zoneRepository = zoneRepository,
            onuTypeRepository = onuTypeRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            taskRepository = taskRepository,
            auditLogRepository = auditLogRepository,
            commandService = commandService,
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

        val response = facade.unconfiguredOnus()

        assertTrue(response.status)
        assertEquals("4857544311E70E9A", response.response[0].sn)
        assertEquals("0", response.response[0].board)
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
        every { zoneRepository.findByName("ZonaA") } returns Optional.of(OltMgrZone(id = 2L, name = "ZonaA"))
        every { onuTypeRepository.findByName("HG8245H") } returns Optional.of(OltMgrOnuType(id = 3L, name = "HG8245H"))
        every { commandService.authorize(any()) } returns AuthorizeCliResult(ontId = 7, commands = emptyList())
        val savedOnu = slot<OltMgrOnu>()
        every { onuRepository.save(capture(savedOnu)) } answers {
            firstArg<OltMgrOnu>().also { it.id = 20L }
        }
        every { onuRepository.findMaxOnuIndex(1L, 0, 2) } returns 6

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
        verify { commandService.authorize(any()) }
        verify { taskRepository.save(match { it.type == "authorize" && it.status == "success" }) }
        verify { auditLogRepository.save(match { it.action == "authorize_onu" && it.onu?.id == 20L }) }
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
        assertEquals("gigafiber-ma5608t_1_0_5", onu.externalId)
        verify { commandService.move(any()) }
        verify { auditLogRepository.save(match { it.action == "move_onu" }) }
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
        verify { commandService.delete(any()) }
        verify { auditLogRepository.save(match { it.action == "delete_onu" }) }
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

    @Test
    fun `updateOnuWan aplica CLI y persiste la red en A`() {
        val onu = givenAuthorizedOnu()
        val cliRequest = slot<UpdateWanCliRequest>()
        every { commandService.updateWan(capture(cliRequest)) } returns emptyList()
        every { onuRepository.save(any()) } answers { firstArg() }

        val response = facade.updateOnuWan(
            "gigafiber-ma5608t_1_0_5",
            UpdateWanFormDto(
                vlan = "120",
                ip_address = "192.168.30.50",
                subnet_mask = "255.255.255.0",
                default_gateway = "192.168.30.1",
                dns1 = "8.8.8.8",
                dns2 = "8.8.4.4"
            )
        )

        assertTrue(response.status)
        assertEquals("gigafiber-ma5608t_1_0_5", response.unique_external_id)
        assertEquals(120, onu.mainVlanId)
        assertEquals("192.168.30.50", onu.ipAddress)
        assertEquals("255.255.255.0", onu.subnetMask)
        assertEquals("192.168.30.1", onu.defaultGateway)
        assertEquals("8.8.8.8", onu.dns1)
        assertEquals("8.8.4.4", onu.dns2)
        assertEquals("static", onu.wanMode)
        assertEquals(1, cliRequest.captured.board)
        assertEquals(0, cliRequest.captured.port)
        assertEquals(5, cliRequest.captured.ontId)
        assertEquals(120, cliRequest.captured.vlan)
        verify { taskRepository.save(match { it.type == "set_wan_mode" && it.status == "success" }) }
        verify { auditLogRepository.save(match { it.action == "set_wan_mode" }) }
    }

    @Test
    fun `updateOnuVlan solo reconfigura la VLAN`() {
        val onu = givenAuthorizedOnu()
        val cliRequest = slot<UpdateWanCliRequest>()
        every { commandService.updateWan(capture(cliRequest)) } returns emptyList()
        every { onuRepository.save(any()) } answers { firstArg() }

        val response = facade.updateOnuVlan("gigafiber-ma5608t_1_0_5", "120")

        assertTrue(response.status)
        assertEquals(120, onu.mainVlanId)
        assertNull(onu.ipAddress)
        assertNull(cliRequest.captured.ipAddress)
        verify { auditLogRepository.save(match { it.action == "update_vlan" }) }
    }

    @Test
    fun `updateOnuWan rechaza IP sin mascara`() {
        givenAuthorizedOnu()

        assertThrows<OltGatewayValidationException> {
            facade.updateOnuWan(
                "gigafiber-ma5608t_1_0_5",
                UpdateWanFormDto(ip_address = "192.168.30.50")
            )
        }
        verify(exactly = 0) { commandService.updateWan(any()) }
    }

    @Test
    fun `updateOnuVlan 404 si no existe`() {
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("missing") } returns Optional.empty()

        assertThrows<OnuNotFoundException> { facade.updateOnuVlan("missing", "120") }
    }

    private fun givenAuthorizedOnu(): OltMgrOnu {
        val onu = OltMgrOnu(
            id = 10L,
            sn = "4857544311E70E9A",
            externalId = "gigafiber-ma5608t_1_0_5",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 5,
            mainVlanId = 100
        )
        every { onuRepository.findByExternalIdAndDeletedAtIsNull("gigafiber-ma5608t_1_0_5") } returns Optional.of(onu)
        return onu
    }
}
