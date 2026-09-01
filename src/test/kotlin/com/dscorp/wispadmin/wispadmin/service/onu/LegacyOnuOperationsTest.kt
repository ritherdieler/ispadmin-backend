package com.dscorp.wispadmin.wispadmin.service.onu

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.service.OltService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.dscorp.wispadmin.wispadmin.response.Onu as SmartOltOnu

class LegacyOnuOperationsTest {

    private val oltService = mockk<OltService>(relaxed = true)
    private lateinit var operations: LegacyOnuOperations

    @BeforeEach
    fun setUp() {
        operations = LegacyOnuOperations(oltService)
    }

    @Test
    fun `moveOnu delega a OltService`() {
        val request = MoveOnuRequest(subscriptionId = 1, newNapBoxId = 2)
        val onu = Onu(sn = "HWTC1")
        val napBox = NapBox()

        operations.moveOnu(request, onu, napBox)

        verify(exactly = 1) { oltService.moveOnu(request, onu, napBox) }
    }

    @Test
    fun `deleteOnu delega a OltService`() {
        operations.deleteOnu("ext-1")

        verify(exactly = 1) { oltService.deleteOnu("ext-1") }
    }

    @Test
    fun `authorizeOnuInSmartOltWidthPostMethod delega a OltService`() {
        val request = OnuAuthorizationRequest(
            olt_id = "1",
            pon_type = "gpon",
            board = "1",
            port = "0",
            sn = "HWTC1",
            vlan = "1100",
            onu_type = "EG8145V5",
            zone = "1",
            name = "Cliente",
            onu_mode = "Routing",
            custom_profile = "Generic_1"
        )

        operations.authorizeOnuInSmartOltWidthPostMethod(request)

        verify(exactly = 1) { oltService.authorizeOnuInSmartOltWidthPostMethod(request) }
    }

    @Test
    fun `deleteOnuBySn resuelve SN y elimina por unique_external_id`() {
        every { oltService.getOnuBySn("HWTC1") } returns OnuBySnResponse(
            onus = listOf(SmartOltOnu().copy(unique_external_id = "ext-9")),
            response_code = "0",
            status = true
        )

        operations.deleteOnuBySn("HWTC1")

        verify(exactly = 1) { oltService.deleteOnu("ext-9") }
    }

    @Test
    fun `rebootOnuBySn resuelve SN y reinicia por unique_external_id`() {
        every { oltService.getOnuBySn("HWTC1") } returns OnuBySnResponse(
            onus = listOf(SmartOltOnu().copy(unique_external_id = "ext-9")),
            response_code = "0",
            status = true
        )

        operations.rebootOnuBySn("HWTC1")

        verify(exactly = 1) { oltService.rebootOnu("ext-9") }
    }

    @Test
    fun `deleteOnuBySn falla si SmartOLT no tiene la ONU`() {
        every { oltService.getOnuBySn("missing") } returns OnuBySnResponse()

        assertThrows(IllegalArgumentException::class.java) {
            operations.deleteOnuBySn("missing")
        }
    }

    @Test
    fun `rebootOnuBySn falla si falta unique_external_id`() {
        every { oltService.getOnuBySn("HWTC1") } returns OnuBySnResponse(
            onus = listOf(SmartOltOnu()),
            response_code = "0",
            status = true
        )

        assertThrows(IllegalStateException::class.java) {
            operations.rebootOnuBySn("HWTC1")
        }
    }
}
