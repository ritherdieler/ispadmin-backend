package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FiberSubscriptionProvisioningServiceTest {

    private val service = FiberSubscriptionProvisioningService()

    @Test
    fun `resolveHostDeviceId usa hostDevice del NapBox cuando esta configurado`() {
        val napBox = NapBox(
            id = 1,
            hostDevice = NetworkDevice(id = 8)
        )

        val resolved = service.resolveHostDeviceId(requestHostDeviceId = 1, napBox = napBox)

        assertEquals(8, resolved)
    }

    @Test
    fun `resolveHostDeviceId usa request cuando NapBox no tiene hostDevice`() {
        val napBox = NapBox(id = 1, hostDevice = null)

        val resolved = service.resolveHostDeviceId(requestHostDeviceId = 8, napBox = napBox)

        assertEquals(8, resolved)
    }

    @Test
    fun `resolveHostDeviceId falla cuando no hay hostDevice en request ni en NapBox`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.resolveHostDeviceId(requestHostDeviceId = 0, napBox = null)
        }
    }

    @Test
    fun `enrichOnuFromNapBox completa board port y olt desde NapBox`() {
        val napBox = NapBox(
            id = 1,
            oltId = 42,
            oltBoard = 3,
            oltPort = 7
        )
        val onu = OnuDto(
            sn = "HWTC12345678",
            onu_type_name = "HG8145V5",
            pon_type = "gpon",
            onu_type_id = "99",
            onu = "1"
        )

        val enriched = service.enrichOnuFromNapBox(onu, napBox)

        assertEquals("3", enriched.board)
        assertEquals("7", enriched.port)
        assertEquals("42", enriched.olt_id)
        assertEquals("HWTC12345678", enriched.sn)
        assertEquals("HG8145V5", enriched.onu_type_name)
    }

    @Test
    fun `enrichOnuFromNapBox conserva valores del cliente cuando NapBox no tiene datos OLT`() {
        val napBox = NapBox(id = 1)
        val onu = OnuDto(
            board = "1",
            port = "2",
            olt_id = "5",
            sn = "HWTC12345678",
            onu_type_name = "HG8145V5",
            pon_type = "gpon"
        )

        val enriched = service.enrichOnuFromNapBox(onu, napBox)

        assertEquals("1", enriched.board)
        assertEquals("2", enriched.port)
        assertEquals("5", enriched.olt_id)
    }
}
