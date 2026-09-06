package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.UnconfirmedOnuResponse
import com.dscorp.wispadmin.wispadmin.util.SmartOltHttpClient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap

class RealOltServiceTest {

    private val smartOlt = mockk<SmartOltHttpClient>(relaxed = true)
    private val gatewayClient = mockk<OltGatewayHttpClient>(relaxed = true)
    private val gateway = mockk<ObjectProvider<OltGatewayHttpClient>>()

    private fun service(gatewayEnabled: Boolean): RealOltService {
        every { gateway.ifAvailable } returns if (gatewayEnabled) gatewayClient else null
        return RealOltService(
            gatewayClientEnabled = gatewayEnabled,
            gatewayHttp = gateway,
            objectMapper = jacksonObjectMapper(),
            smartOltHttpClient = smartOlt,
        )
    }

    private val authorizeRequest = OnuAuthorizationRequest(
        olt_id = "gigafiber-ma5608t",
        pon_type = "gpon",
        board = "1",
        port = "6",
        sn = "ZTEGDC47BFFD",
        vlan = "100",
        onu_type = "F6600RV9.0.21",
        zone = "Zone 1",
        name = "lab",
        onu_mode = "Routing",
        custom_profile = "Generic_1",
    )

    @Test
    fun `falls back to SmartOLT HTTP when gateway client is disabled`() {
        every { smartOlt.get("onu/unconfigured_onus", UnconfirmedOnuResponse::class.java) } returns
            UnconfirmedOnuResponse(emptyList(), true)

        assertEquals(emptyList<com.dscorp.wispadmin.wispadmin.response.Response>(), service(false).getUnConfiguredOnus())
        verify(exactly = 1) { smartOlt.get("onu/unconfigured_onus", UnconfirmedOnuResponse::class.java) }
    }

    @Test
    fun `authorize delete reboot move and by-sn go to gateway when client is enabled`() {
        every { gatewayClient.postForm(any(), any()) } returns
            ResponseEntity.ok("""{"status":true,"unique_external_id":"ext-1"}""")
        every { gatewayClient.postJson(any()) } returns ResponseEntity.ok("""{"status":true}""")
        every { gatewayClient.getJson("/api/olt-gateway/onu/get_onus_details_by_sn/ZTEGDC47BFFD") } returns
            ResponseEntity.ok("""{"onus":[],"response_code":"200","status":true}""")

        val olt = service(true)
        olt.authorizeOnuInSmartOltWidthPostMethod(authorizeRequest)
        olt.deleteOnu("ext-1")
        olt.rebootOnu("ext-1")
        olt.moveOnu(
            MoveOnuRequest(subscriptionId = 1, newNapBoxId = 2),
            Onu(sn = "ZTEGDC47BFFD"),
            NapBox(oltId = 2, oltBoard = 1, oltPort = 7),
        )
        val bySn = olt.getOnuBySn("ZTEGDC47BFFD")

        assertEquals(true, bySn.status)
        verify { gatewayClient.postForm("/api/olt-gateway/onu/authorize_onu", any<MultiValueMap<String, String>>()) }
        verify { gatewayClient.postJson("/api/olt-gateway/onu/delete/ext-1") }
        verify { gatewayClient.postJson("/api/olt-gateway/onu/reboot/ext-1") }
        verify { gatewayClient.postForm("/api/olt-gateway/onu/move/ZTEGDC47BFFD", any<MultiValueMap<String, String>>()) }
        verify(exactly = 0) { smartOlt.post(any(), any(), Any::class.java) }
        verify(exactly = 0) { smartOlt.get(any(), OnuBySnResponse::class.java) }
    }

    @Test
    fun `authorize falls back to SmartOLT HTTP when gateway client is disabled`() {
        service(false).authorizeOnuInSmartOltWidthPostMethod(authorizeRequest)

        verify { smartOlt.post("onu/authorize_onu", any(), Any::class.java) }
        verify(exactly = 0) { gatewayClient.postForm(any(), any()) }
    }
}
