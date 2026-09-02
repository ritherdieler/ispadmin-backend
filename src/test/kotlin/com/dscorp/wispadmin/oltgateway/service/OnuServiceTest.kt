package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredItemDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import com.dscorp.wispadmin.wispadmin.service.OltService
import com.dscorp.wispadmin.oltgateway.dto.AutofindRefreshResultDto
import com.dscorp.wispadmin.oltgateway.dto.CatalogItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuFilter
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuPageDto
import com.dscorp.wispadmin.oltgateway.dto.OnuCatalogsDto
import com.dscorp.wispadmin.oltgateway.dto.SyncJobStatusDto
import com.dscorp.wispadmin.oltgateway.service.OltInventorySyncService
import com.dscorp.wispadmin.oltgateway.service.OltSignalPollService
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.server.ResponseStatusException

class OnuServiceTest {

    private val oltService = mockk<OltService>()
    private val inventoryProvider = mockk<ObjectProvider<OltInventorySyncService>>()
    private val signalProvider = mockk<ObjectProvider<OltSignalPollService>>()
    private val importProvider = mockk<ObjectProvider<com.dscorp.wispadmin.oltgateway.service.SmartOltImportService>>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val autofindProvider = mockk<ObjectProvider<OltAutofindCacheService>>()
    private val managerFacadeProvider = mockk<ObjectProvider<OltManagerFacade>>()
    private val syncJobRunnerProvider = mockk<ObjectProvider<OltGatewaySyncJobRunner>>()
    private val propertiesProvider = mockk<ObjectProvider<OltGatewayProperties>>()
    private val inventorySync = mockk<OltInventorySyncService>()
    private val signalPoll = mockk<OltSignalPollService>()
    private val autofindCache = mockk<OltAutofindCacheService>()
    private val managerFacade = mockk<OltManagerFacade>()
    private val syncJobRunner = mockk<OltGatewaySyncJobRunner>()
    private val properties = OltGatewayProperties()
    private val writeRouter = mockk<OnuWriteRouter>(relaxed = true)
    private lateinit var service: OnuService

    @BeforeEach
    fun setUp() {
        service = OnuService(
            oltService,
            inventoryProvider,
            signalProvider,
            importProvider,
            subscriptionRepository,
            autofindProvider,
            managerFacadeProvider,
            syncJobRunnerProvider,
            propertiesProvider,
            writeRouter
        )
        every { subscriptionRepository.findActiveIpSnPairsByFiberOnuSnIn(any()) } returns emptyList()
        every { subscriptionRepository.findActiveIpNamePairsByFullNameIn(any()) } returns emptyList()
        every { propertiesProvider.getIfAvailable() } returns properties
        every { autofindProvider.getIfAvailable() } returns autofindCache
        every { managerFacadeProvider.getIfAvailable() } returns managerFacade
        every { syncJobRunnerProvider.getIfAvailable() } returns syncJobRunner
        properties.autofind.enabled = true
    }

    @Test
    fun `listConfigured delega al inventory sync del gateway`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every {
            inventorySync.listConfigured(0, 50, match { it.q == "SN" && it.board == 1 && it.port == 2 && it.runState == "online" && it.signalCategory == "Good" })
        } returns ConfiguredOnuPageDto(
            items = listOf(
                ConfiguredOnuItemDto(
                    id = 1L,
                    sn = "SN1",
                    externalId = "e1",
                    board = 1,
                    port = 2,
                    onuIndex = 0,
                    name = "n",
                    importedFromOlt = true,
                    runState = "online",
                    matchState = null,
                    polledAt = null,
                    signalCategory = "Good"
                )
            ),
            page = 0,
            size = 50,
            totalElements = 1,
            totalPages = 1
        )

        val page = service.listConfigured(
            0,
            50,
            ConfiguredOnuFilter(q = "SN", board = 1, port = 2, runState = "online", signalCategory = "Good")
        )

        assertEquals(1, page.totalElements)
        assertEquals("SN1", page.items[0].sn)
    }

    @Test
    fun `listConfigured enriquece ipAddress desde subscription activa por SN`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every { inventorySync.listConfigured(0, 50, any()) } returns ConfiguredOnuPageDto(
            items = listOf(
                ConfiguredOnuItemDto(
                    id = 1L,
                    sn = "HWTC15F5EE76",
                    externalId = "e1",
                    board = 1,
                    port = 2,
                    onuIndex = 0,
                    name = "ivan gamez ocana",
                    importedFromOlt = true,
                    runState = "online",
                    matchState = null,
                    polledAt = null,
                    ipAddress = null
                )
            ),
            page = 0,
            size = 50,
            totalElements = 1,
            totalPages = 1
        )
        every {
            subscriptionRepository.findActiveIpSnPairsByFiberOnuSnIn(
                match { keys -> keys.contains("HWTC15F5EE76") && keys.contains("15F5EE76") }
            )
        } returns listOf(arrayOf("HWTC15F5EE76", "192.168.221.17"))
        every { subscriptionRepository.findActiveIpNamePairsByFullNameIn(any()) } returns emptyList()

        val page = service.listConfigured(0, 50)

        assertEquals("192.168.221.17", page.items[0].ipAddress)
    }

    @Test
    fun `listConfigured enriquece ipAddress por nombre cuando no hay fiberOnu`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every { inventorySync.listConfigured(0, 50, any()) } returns ConfiguredOnuPageDto(
            items = listOf(
                ConfiguredOnuItemDto(
                    id = 2L,
                    sn = "HWTC15F5D926",
                    externalId = "e2",
                    board = 1,
                    port = 2,
                    onuIndex = 1,
                    name = "JUDITH MIREYA ESPINOZA GUIMAREY",
                    importedFromOlt = true,
                    runState = "online",
                    matchState = null,
                    polledAt = null,
                    ipAddress = null
                )
            ),
            page = 0,
            size = 50,
            totalElements = 1,
            totalPages = 1
        )
        every { subscriptionRepository.findActiveIpSnPairsByFiberOnuSnIn(any()) } returns emptyList()
        every {
            subscriptionRepository.findActiveIpNamePairsByFullNameIn(
                match { names -> names.contains("judith mireya espinoza guimarey") }
            )
        } returns listOf(arrayOf("judith mireya espinoza guimarey", "192.168.22.54"))

        val page = service.listConfigured(0, 50)

        assertEquals("192.168.22.54", page.items[0].ipAddress)
    }

    @Test
    fun `listConfigured conserva ipAddress del inventario si ya existe`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every { inventorySync.listConfigured(0, 50, any()) } returns ConfiguredOnuPageDto(
            items = listOf(
                ConfiguredOnuItemDto(
                    id = 1L,
                    sn = "HWTC15F5EE76",
                    externalId = "e1",
                    board = 1,
                    port = 2,
                    onuIndex = 0,
                    name = "ivan",
                    importedFromOlt = true,
                    runState = "online",
                    matchState = null,
                    polledAt = null,
                    ipAddress = "10.0.0.9"
                )
            ),
            page = 0,
            size = 50,
            totalElements = 1,
            totalPages = 1
        )

        val page = service.listConfigured(0, 50)

        assertEquals("10.0.0.9", page.items[0].ipAddress)
        verify(exactly = 0) { subscriptionRepository.findActiveIpSnPairsByFiberOnuSnIn(any()) }
        verify(exactly = 0) { subscriptionRepository.findActiveIpNamePairsByFullNameIn(any()) }
    }

    @Test
    fun `listConfigured falla si gateway deshabilitado`() {
        every { inventoryProvider.getIfAvailable() } returns null
        assertThrows(ResponseStatusException::class.java) {
            service.listConfigured(0, 50)
        }
    }

    @Test
    fun `listCatalogs delega al gateway`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every { inventorySync.listCatalogs() } returns OnuCatalogsDto(
            olts = listOf(CatalogItemDto(1L, "olt-a")),
            zones = listOf(CatalogItemDto(2L, "Zone 1"))
        )

        val catalogs = service.listCatalogs()

        assertEquals(1, catalogs.olts.size)
        assertEquals("Zone 1", catalogs.zones[0].name)
    }

    @Test
    fun `unconfigured se sirve del cache propio sin llamar a SmartOLT`() {
        every { autofindCache.listUnconfigured() } returns listOf(
            SmartOltUnconfiguredItemDto(
                board = "1",
                olt_id = "gigafiber-ma5608t",
                onu = "",
                onu_type_id = "",
                onu_type_name = "EG8145V5",
                pon_type = "gpon",
                port = "0",
                sn = "HWTC0086CD49"
            )
        )

        val list = service.getUnConfiguredOnus()

        assertEquals(1, list.size)
        assertEquals("HWTC0086CD49", list[0].sn)
        assertEquals("1", list[0].board)
        verify(exactly = 0) { oltService.getUnConfiguredOnus() }
        verify(exactly = 0) { autofindCache.refreshLive() }
    }

    @Test
    fun `unconfigured con refresh fuerza una lectura en vivo antes de responder`() {
        every { autofindCache.refreshLive() } returns AutofindRefreshResultDto(source = "live", seen = 1, stored = 1)
        every { autofindCache.listUnconfigured() } returns emptyList()

        service.getUnConfiguredOnus(forceRefresh = true)

        verify(exactly = 1) { autofindCache.refreshLive() }
        verify(exactly = 1) { autofindCache.listUnconfigured() }
    }

    @Test
    fun `unconfigured cae a SmartOLT si el autofind propio esta apagado`() {
        properties.autofind.enabled = false
        every { oltService.getUnConfiguredOnus() } returns listOf(
            Response("1", "gigafiber-ma5608t", "", "", "EG8145V5", "gpon", "0", "HWTC0086CD49")
        )

        val list = service.getUnConfiguredOnus()

        assertEquals("HWTC0086CD49", list[0].sn)
        verify(exactly = 1) { oltService.getUnConfiguredOnus() }
        verify(exactly = 0) { autofindCache.listUnconfigured() }
    }

    @Test
    fun `getOnuBySn se sirve del gateway sin tocar SmartOLT`() {
        every { managerFacade.getOnusDetailsBySn("HWTC0086CD49") } returns SmartOltOnuBySnResponseDto(
            onus = listOf(SmartOltOnuDto(sn = "HWTC0086CD49", unique_external_id = "ext-1", board = "1", port = "0")),
            response_code = "200",
            status = true
        )

        val response = service.getOnuBySn("HWTC0086CD49")

        assertEquals("HWTC0086CD49", response.onus[0].sn)
        assertEquals("ext-1", response.onus[0].unique_external_id)
        verify(exactly = 0) { oltService.getOnuBySn(any()) }
    }

    @Test
    fun `getOnuBySn devuelve vacio sin ir a SmartOLT cuando el gateway no conoce el serial`() {
        every { managerFacade.getOnusDetailsBySn("HWTCDESCONOCIDO") } throws OnuNotFoundException("no existe")

        val response = service.getOnuBySn("HWTCDESCONOCIDO")

        assertTrue(response.onus.isEmpty())
        assertEquals("404", response.response_code)
        verify(exactly = 0) { oltService.getOnuBySn(any()) }
    }

    @Test
    fun `getOnuBySn cae a SmartOLT si el gateway falla`() {
        every { managerFacade.getOnusDetailsBySn("HWTC0086CD49") } throws IllegalStateException("olt_unreachable")
        every { oltService.getOnuBySn("HWTC0086CD49") } returns OnuBySnResponse(
            onus = emptyList(),
            response_code = "200",
            status = true
        )

        service.getOnuBySn("HWTC0086CD49")

        verify(exactly = 1) { oltService.getOnuBySn("HWTC0086CD49") }
    }

    @Test
    fun `authorizeOnu delega en el enrutador de escrituras`() {
        every { writeRouter.authorize(any<AuthorizeOnuFormDto>()) } returns
            SmartOltActionResponseDto(status = true)

        val result = service.authorizeOnu(
            AuthorizeOnuFormDto(
                olt_id = "gigafiber-ma5608t",
                board = "1",
                port = "0",
                sn = "HWTC0086CD49",
                vlan = "1100",
                name = "Cliente",
                onu_type = "EG8145V5"
            )
        )

        assertTrue(result.status)
        verify(exactly = 1) { writeRouter.authorize(match<AuthorizeOnuFormDto> { it.sn == "HWTC0086CD49" }) }
        verify(exactly = 0) { oltService.authorizeOnuInSmartOltWidthPostMethod(any()) }
    }

    @Test
    fun `authorizeOnuInSmartOltWidthPostMethod delega en el enrutador de escrituras`() {
        every { writeRouter.authorize(any<OnuAuthorizationRequest>()) } returns
            SmartOltActionResponseDto(status = true)

        service.authorizeOnuInSmartOltWidthPostMethod(
            OnuAuthorizationRequest(
                olt_id = "1",
                pon_type = "gpon",
                board = "1",
                port = "0",
                sn = "HWTC0086CD49",
                vlan = "1100",
                onu_type = "EG8145V5",
                zone = "Zone 1",
                name = "Cliente",
                onu_mode = "Routing",
                custom_profile = "Generic_1"
            )
        )

        verify(exactly = 1) {
            writeRouter.authorize(match<OnuAuthorizationRequest> { it.sn == "HWTC0086CD49" && it.zone == "Zone 1" })
        }
    }

    @Test
    fun `getConfiguredByExternalId delega y enriquece ip desde subscription`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every { inventorySync.getConfiguredByExternalId("ext-1") } returns com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuDetailDto(
            id = 1L,
            sn = "HWTCABCDEF01",
            externalId = "ext-1",
            board = 0,
            port = 1,
            onuIndex = 2,
            name = "Cliente",
            importedFromOlt = true,
            runState = "online",
            matchState = "match",
            polledAt = null,
            ipAddress = null
        )
        every {
            subscriptionRepository.findActiveIpSnPairsByFiberOnuSnIn(any())
        } returns listOf(arrayOf("HWTCABCDEF01", "192.168.221.17"))

        val detail = service.getConfiguredByExternalId("ext-1")

        assertEquals("192.168.221.17", detail.ipAddress)
        verify(exactly = 1) { inventorySync.getConfiguredByExternalId("ext-1") }
    }

    @Test
    fun `getConfiguredByExternalId lanza 404 si no existe`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every { inventorySync.getConfiguredByExternalId("missing") } returns null

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.getConfiguredByExternalId("missing")
        }
        assertEquals(404, ex.status.value())
    }

    @Test
    fun `getConfiguredLiveStatus delega al inventory sync`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every { inventorySync.getLiveStatusByExternalId("ext-1") } returns com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuLiveStatusDto(
            sn = "HWTCABCDEF01",
            runState = "online",
            onuRxDbm = -21.0
        )

        val live = service.getConfiguredLiveStatus("ext-1")

        assertEquals("online", live.runState)
        assertEquals(-21.0, live.onuRxDbm)
        verify(exactly = 1) { inventorySync.getLiveStatusByExternalId("ext-1") }
    }

    @Test
    fun `getConfiguredLiveStatus lanza 404 si no existe`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every { inventorySync.getLiveStatusByExternalId("missing") } returns null

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.getConfiguredLiveStatus("missing")
        }
        assertEquals(404, ex.status.value())
    }

    @Test
    fun `getConfiguredHistory delega al inventory sync`() {
        every { inventoryProvider.getIfAvailable() } returns inventorySync
        every { inventorySync.getHistoryByExternalId("ext-1", 50) } returns com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuHistoryDto(
            items = listOf(
                com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuHistoryItemDto(
                    id = 1L,
                    action = "reboot_onu",
                    createdAt = "2026-08-27T15:00:00Z"
                )
            )
        )

        val history = service.getConfiguredHistory("ext-1", 50)

        assertEquals(1, history.items.size)
        assertEquals("reboot_onu", history.items[0].action)
        verify(exactly = 1) { inventorySync.getHistoryByExternalId("ext-1", 50) }
    }

    @Test
    fun `rebootConfiguredOnu delega en el enrutador de escrituras`() {
        every { writeRouter.reboot("ext-1") } returns
            SmartOltActionResponseDto(status = true, unique_external_id = "ext-1")

        val result = service.rebootConfiguredOnu("ext-1")

        assertTrue(result.status)
        verify(exactly = 1) { writeRouter.reboot("ext-1") }
        verify(exactly = 0) { oltService.rebootOnu(any()) }
    }

    @Test
    fun `deleteConfiguredOnu delega en el enrutador de escrituras`() {
        every { writeRouter.delete("ext-1") } returns
            SmartOltActionResponseDto(status = true, unique_external_id = "ext-1")

        val result = service.deleteConfiguredOnu("ext-1")

        assertTrue(result.status)
        verify(exactly = 1) { writeRouter.delete("ext-1") }
        verify(exactly = 0) { oltService.deleteOnu(any()) }
    }

    @Test
    fun `borrar y reiniciar por SN delegan en el enrutador para que resuelva el identificador`() {
        service.deleteOnuBySn("HWTC0086CD49")
        service.rebootOnuBySn("HWTC0086CD49")

        verify(exactly = 1) { writeRouter.deleteBySn("HWTC0086CD49") }
        verify(exactly = 1) { writeRouter.rebootBySn("HWTC0086CD49") }
        verify(exactly = 0) { oltService.getOnuBySn(any()) }
    }

    @Test
    fun `los sync arrancan en segundo plano y responden el estado del trabajo`() {
        every { syncJobRunner.startSnmpInventory() } returns SyncJobStatusDto(
            job = OltGatewaySyncJobRunner.JOB_SNMP_INVENTORY,
            started = true,
            running = true
        )
        every { syncJobRunner.startSignal() } returns SyncJobStatusDto(
            job = OltGatewaySyncJobRunner.JOB_SIGNAL,
            started = true,
            running = true
        )

        val inv = service.startInventorySync()
        val sig = service.startSignalSync()

        assertTrue(inv.started)
        assertTrue(sig.started)
        verify(exactly = 0) { inventorySync.syncInventoryFromSnmp() }
        verify(exactly = 0) { signalPoll.pollSignalsFromSnmp() }
    }

    @Test
    fun `arrancar un sync falla si el gateway esta deshabilitado`() {
        every { syncJobRunnerProvider.getIfAvailable() } returns null

        assertThrows(ResponseStatusException::class.java) { service.startInventorySync() }
        assertThrows(ResponseStatusException::class.java) { service.startSignalSync() }
    }
}
