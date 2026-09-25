package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OnuGovernanceDeleteServiceTest {
    private val manager = mockk<OltManagerFacade>()
    private val ownerships = mockk<ProvisioningV2OnuOwnershipService>(relaxed = true)
    private val activation = mockk<OnuActivationService>(relaxed = true)
    private val service = OnuGovernanceDeleteService(manager, ownerships, activation)

    @Test
    fun `borra la ONT si existe y suelta solo la reserva de ese SN`() {
        every { ownerships.find("HWTC1") } returns ownership("HWTC1", "ext-1")
        every { manager.externalIdBySn("HWTC1") } returns "ext-1"
        every { manager.deleteOnu("ext-1") } returns SmartOltActionResponseDto(status = true, unique_external_id = "ext-1")

        val result = service.deleteBySn("hwtc1")

        assertEquals("ext-1", result.unique_external_id)
        verify { manager.deleteOnu("ext-1") }
        verify { ownerships.releaseSerial("HWTC1") }
        verify { activation.clearJournal("HWTC1") }
        verify(exactly = 0) { ownerships.releaseSerial("OTHER") }
    }

    @Test
    fun `reserva sin ONT solo libera fila y journal`() {
        every { ownerships.find("HWTCRESERVED") } returns ownership("HWTCRESERVED", null)
        every { manager.externalIdBySn("HWTCRESERVED") } returns null

        service.deleteBySn("HWTCRESERVED")

        verify(exactly = 0) { manager.deleteOnu(any()) }
        verify { ownerships.releaseSerial("HWTCRESERVED") }
        verify { activation.clearJournal("HWTCRESERVED") }
    }

    @Test
    fun `sin inventario ni reserva responde no encontrada`() {
        every { ownerships.find("MISSING") } returns null
        every { manager.externalIdBySn("MISSING") } returns null

        assertThrows<OnuNotFoundException> { service.deleteBySn("MISSING") }
        verify(exactly = 0) { ownerships.releaseSerial(any()) }
    }

    private fun ownership(sn: String, externalId: String?) = ProvisioningV2OnuOwnership(
        serial = sn,
        operationId = "op",
        callerEnv = "staging",
        requestHash = "hash",
        externalId = externalId,
        board = null,
        port = null,
        ontId = null,
        stage = "CLAIMED",
    )
}
