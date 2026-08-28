package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredItemDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuItemDto
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuPageDto
import com.dscorp.wispadmin.oltgateway.dto.HealthResponseDto
import com.dscorp.wispadmin.oltgateway.dto.SignalPollResultDto
import com.dscorp.wispadmin.oltgateway.dto.SignalPollStatusDto
import com.dscorp.wispadmin.oltgateway.dto.SyncStatusDto
import com.dscorp.wispadmin.oltgateway.exception.OltGatewayExceptionHandler
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import com.dscorp.wispadmin.oltgateway.service.OltGatewayQueryFacade
import com.dscorp.wispadmin.oltgateway.service.OltInventorySyncService
import com.dscorp.wispadmin.oltgateway.service.OltSignalPollService
import com.dscorp.wispadmin.oltgateway.service.SignalPollResult
import com.dscorp.wispadmin.oltgateway.service.SyncResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class OltGatewayControllerTest {

    private val queryFacade = mockk<OltGatewayQueryFacade>()
    private val inventorySyncService = mockk<OltInventorySyncService>()
    private val signalPollService = mockk<OltSignalPollService>()
    private val smartOltImportService = mockk<com.dscorp.wispadmin.oltgateway.service.SmartOltImportService>()
    private val properties = com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties()
    private val trapBuffer = com.dscorp.wispadmin.oltgateway.snmp.RecentOltSnmpTrapBuffer(10)

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            OltGatewayController(
                queryFacade,
                inventorySyncService,
                signalPollService,
                smartOltImportService,
                properties,
                trapBuffer
            )
        )
        .setControllerAdvice(OltGatewayExceptionHandler())
        .build()

    @Test
    fun `health retorna estado`() {
        every { queryFacade.health() } returns HealthResponseDto("UP", true, 12)

        mockMvc.perform(get("/api/olt-gateway/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.oltReachable").value(true))
            .andExpect(jsonPath("$.latencyMs").value(12))
    }

    @Test
    fun `autofind retorna contrato SmartOLT`() {
        every { queryFacade.autofind() } returns SmartOltUnconfiguredOnusResponseDto(
            response = listOf(
                SmartOltUnconfiguredItemDto(
                    board = "0",
                    olt_id = "gigafiber-ma5608t",
                    onu = "",
                    onu_type_id = "",
                    onu_type_name = "HG8245H",
                    pon_type = "gpon",
                    port = "2",
                    sn = "4857544311E70E9A"
                )
            ),
            status = true
        )

        mockMvc.perform(get("/api/olt-gateway/onus/autofind"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.response[0].sn").value("4857544311E70E9A"))
            .andExpect(jsonPath("$.response[0].board").value("0"))
            .andExpect(jsonPath("$.response[0].pon_type").value("gpon"))

        verify { queryFacade.autofind() }
    }

    @Test
    fun `by-sn retorna 404 cuando no existe`() {
        every { queryFacade.bySn("NOSUCHSN1") } throws OnuNotFoundException("ONU not found for SN=NOSUCHSN1")

        mockMvc.perform(get("/api/olt-gateway/onus/by-sn/NOSUCHSN1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("onu_not_found"))
    }

    @Test
    fun `by-sn retorna OnuBySnResponse`() {
        every { queryFacade.bySn("4857544311E70E9A") } returns SmartOltOnuBySnResponseDto(
            onus = emptyList(),
            response_code = "200",
            status = true
        )

        mockMvc.perform(get("/api/olt-gateway/onus/by-sn/4857544311E70E9A"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.response_code").value("200"))
    }

    @Test
    fun `admin sync inventory dispara sync y retorna SyncResult`() {
        every { inventorySyncService.syncInventory() } returns SyncResult(
            inserted = 2,
            updated = 1,
            softDeleted = 0,
            unchanged = 3,
            durationMs = 120
        )

        mockMvc.perform(post("/api/olt-gateway/admin/sync/inventory"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.inserted").value(2))
            .andExpect(jsonPath("$.updated").value(1))
            .andExpect(jsonPath("$.unchanged").value(3))
            .andExpect(jsonPath("$.durationMs").value(120))

        verify(exactly = 1) { inventorySyncService.syncInventory() }
    }

    @Test
    fun `admin sync snmp-inventory dispara syncInventoryFromSnmp`() {
        every { inventorySyncService.syncInventoryFromSnmp() } returns SyncResult(
            inserted = 5,
            updated = 0,
            softDeleted = 0,
            unchanged = 10,
            durationMs = 40
        )

        mockMvc.perform(post("/api/olt-gateway/admin/sync/snmp-inventory"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.inserted").value(5))
            .andExpect(jsonPath("$.unchanged").value(10))

        verify(exactly = 1) { inventorySyncService.syncInventoryFromSnmp() }
    }

    @Test
    fun `admin sync signal dispara poll y retorna SignalPollResult`() {
        every { signalPollService.pollSignals() } returns SignalPollResult(
            slotsPolled = 2,
            portsPolled = 32,
            onusUpdated = 100,
            durationMs = 450
        )

        mockMvc.perform(post("/api/olt-gateway/admin/sync/signal"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slotsPolled").value(2))
            .andExpect(jsonPath("$.portsPolled").value(32))
            .andExpect(jsonPath("$.onusUpdated").value(100))
            .andExpect(jsonPath("$.durationMs").value(450))

        verify(exactly = 1) { signalPollService.pollSignals() }
    }

    @Test
    fun `admin sync status retorna inventory signal y bus`() {
        every { inventorySyncService.status() } returns SyncStatusDto(
            running = false,
            lastStartedAt = "2026-07-17T12:00:00Z",
            lastResult = null,
            busQueueDepth = 2,
            busBusyJobType = "INVENTORY"
        )
        every { signalPollService.status() } returns SignalPollStatusDto(
            running = false,
            lastStartedAt = "2026-07-17T12:05:00Z",
            lastResult = SignalPollResultDto(
                slotsPolled = 2,
                portsPolled = 32,
                onusUpdated = 10,
                durationMs = 200
            ),
            busQueueDepth = 0,
            busBusyJobType = null
        )

        mockMvc.perform(get("/api/olt-gateway/admin/sync/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.running").value(false))
            .andExpect(jsonPath("$.lastStartedAt").value("2026-07-17T12:00:00Z"))
            .andExpect(jsonPath("$.signalRunning").value(false))
            .andExpect(jsonPath("$.signalLastStartedAt").value("2026-07-17T12:05:00Z"))
            .andExpect(jsonPath("$.signalLastResult.onusUpdated").value(10))
            .andExpect(jsonPath("$.busQueueDepth").value(2))
            .andExpect(jsonPath("$.busBusyJobType").value("INVENTORY"))
    }

    @Test
    fun `onus configured retorna pagina desde DB`() {
        every { inventorySyncService.listConfigured(0, 50, any()) } returns ConfiguredOnuPageDto(
            items = listOf(
                ConfiguredOnuItemDto(
                    id = 1L,
                    sn = "4857544311E70E9A",
                    externalId = "gigafiber-ma5608t_0_2_1",
                    board = 0,
                    port = 2,
                    onuIndex = 1,
                    name = "cliente",
                    importedFromOlt = true,
                    runState = "online",
                    matchState = "match",
                    polledAt = "2026-07-17T12:00:00Z"
                )
            ),
            page = 0,
            size = 50,
            totalElements = 1,
            totalPages = 1
        )

        mockMvc.perform(get("/api/olt-gateway/onus/configured"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].sn").value("4857544311E70E9A"))
            .andExpect(jsonPath("$.items[0].runState").value("online"))
    }

    @Test
    fun `traps recent retorna buffer vacio cuando listener off`() {
        mockMvc.perform(get("/api/olt-gateway/admin/snmp/traps/recent"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.listenPort").value(1162))
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.items").isEmpty)
    }
}
