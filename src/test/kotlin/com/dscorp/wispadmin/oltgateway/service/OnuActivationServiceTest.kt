package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.events.RecordingEventBus
import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.client.AcsCpeClient
import com.dscorp.wispadmin.oltgateway.client.AcsCpeProvisionRequest
import com.dscorp.wispadmin.oltgateway.client.AcsCpeProvisionResponse
import com.dscorp.wispadmin.oltgateway.dto.CpeProvisionStatus
import com.dscorp.wispadmin.oltgateway.dto.OltActivationStatus
import com.dscorp.wispadmin.oltgateway.dto.OnuActivateRequestDto
import com.dscorp.wispadmin.oltgateway.exception.OltGatewayConflictException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class OnuActivationServiceTest {

    private val facade = mockk<OltManagerFacade>()
    private val acs = mockk<AcsCpeClient>()
    private val events = RecordingEventBus()

    @Test
    fun `olt failure does not call ACS and returns cpe NA`() {
        every { facade.authorizeOnu(any()) } throws IllegalStateException("CLI timeout")
        val service = OnuActivationService(facade, acs, events) { it.run() }

        val result = service.activate(activateRequest())

        assertEquals(OltActivationStatus.FAILED, result.oltStatus)
        assertEquals(CpeProvisionStatus.NA, result.cpeStatus)
        assertTrue(result.message!!.contains("CLI timeout"))
        verify(exactly = 0) { acs.provision(any()) }
    }

    @Test
    fun `olt ok kicks ACS async and returns partial PENDING without waiting`() {
        every { facade.authorizeOnu(any()) } returns SmartOltActionResponseDto(
            status = true,
            unique_external_id = "gigafiber-ma5608t_1_0_5",
        )
        val started = AtomicBoolean(false)
        val blocked = AtomicBoolean(true)
        every { acs.provision(any()) } answers {
            started.set(true)
            while (blocked.get()) {
                Thread.sleep(10)
            }
            AcsCpeProvisionResponse(status = CpeProvisionStatus.COMPLETE, sn = "ALCL12345678")
        }
        val service = OnuActivationService(facade, acs, events)

        val result = service.activate(activateRequest())
        blocked.set(false)

        assertEquals(OltActivationStatus.COMPLETE, result.oltStatus)
        assertEquals(CpeProvisionStatus.PENDING, result.cpeStatus)
        assertEquals("gigafiber-ma5608t_1_0_5", result.uniqueExternalId)
        assertEquals("ALCL12345678", result.sn)
        assertNull(result.message)
        Thread.sleep(50)
        assertTrue(started.get())
    }

    @Test
    fun `already authorized ONU still kicks ACS and returns olt COMPLETE`() {
        every { facade.authorizeOnu(any()) } throws OltGatewayConflictException("already authorized")
        every { facade.externalIdBySn("ALCL12345678") } returns "gigafiber-ma5608t_1_0_3"
        every { acs.provision(any()) } returns AcsCpeProvisionResponse(
            status = CpeProvisionStatus.PENDING,
            sn = "ALCL12345678",
        )
        val service = OnuActivationService(facade, acs, events) { it.run() }

        val result = service.activate(activateRequest())

        assertEquals(OltActivationStatus.COMPLETE, result.oltStatus)
        assertEquals(CpeProvisionStatus.PENDING, result.cpeStatus)
        assertEquals("gigafiber-ma5608t_1_0_3", result.uniqueExternalId)
        verify(exactly = 1) { acs.provision(match<AcsCpeProvisionRequest> { it.sn == "ALCL12345678" }) }
    }

    @Test
    fun `activation status is stored after olt ok`() {
        every { facade.authorizeOnu(any()) } returns SmartOltActionResponseDto(
            status = true,
            unique_external_id = "gigafiber-ma5608t_1_0_5",
        )
        every { acs.provision(any()) } returns AcsCpeProvisionResponse(
            status = CpeProvisionStatus.PENDING,
            sn = "ALCL12345678",
        )
        val service = OnuActivationService(facade, acs, events) { it.run() }
        service.activate(activateRequest())

        val bySn = service.statusBySn("ALCL12345678")!!
        assertEquals(OltActivationStatus.COMPLETE, bySn.oltStatus)
        assertEquals(CpeProvisionStatus.PENDING, bySn.cpeStatus)
        val byId = service.statusByExternalId("gigafiber-ma5608t_1_0_5")!!
        assertEquals("ALCL12345678", byId.sn)
    }

    @Test
    fun `ACS completion publishes cpe provisioning event`() {
        every { facade.authorizeOnu(any()) } returns SmartOltActionResponseDto(
            status = true,
            unique_external_id = "gigafiber-ma5608t_1_0_5",
        )
        every { acs.provision(any()) } returns AcsCpeProvisionResponse(
            status = CpeProvisionStatus.COMPLETE,
            sn = "ALCL12345678",
        )
        val service = OnuActivationService(facade, acs, events) { it.run() }
        service.activate(activateRequest())

        assertEquals(CpeProvisionStatus.COMPLETE, service.statusBySn("ALCL12345678")!!.cpeStatus)
        assertTrue(events.published.any { it.type == PlatformEventTypes.CPE_PROVISIONING && it.sn == "ALCL12345678" })
    }

    private fun activateRequest() = OnuActivateRequestDto(
        oltId = "1",
        ponType = "gpon",
        board = "1",
        port = "1",
        sn = "ALCL12345678",
        vlan = "100",
        onuType = "HG8240H",
        zone = "Zone 1",
        name = "Juan Perez",
        onuMode = "Routing",
        customProfile = "Generic_1",
        ip = "192.168.1.10",
        ipSegment = "192.168.1.1/24",
        wifiSsid24 = "lab-24",
        wifiPassword24 = "password24",
        wifiSsid5 = "lab-24 - 5G",
        wifiPassword5 = "password24",
    )
}
