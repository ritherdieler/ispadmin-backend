package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.service.OltManagerFacade
import com.dscorp.wispadmin.oltgateway.service.OltServicePortService
import com.dscorp.wispadmin.oltgateway.service.ProvisioningV2OnuOwnership
import com.dscorp.wispadmin.oltgateway.service.ProvisioningV2OnuOwnershipService
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class OnuV2AuthorizationControllerCancellationTest {
    private val manager = mockk<OltManagerFacade>()
    private val servicePorts = mockk<OltServicePortService>(relaxed = true)
    private val ownerships = mockk<ProvisioningV2OnuOwnershipService>()
    private val controller = OnuV2AuthorizationController(manager, servicePorts, ownerships)

    @Test
    fun `compensation is idempotent when reservation and ONU configuration are already absent`() {
        every { ownerships.find(SERIAL) } returns null
        every { ownerships.requireOwner(SERIAL, OPERATION_ID) } throws ResponseStatusException(HttpStatus.NOT_FOUND)
        every { manager.externalIdBySn(SERIAL) } returns null

        val response = controller.compensate(OnuV2CompensateRequest(OPERATION_ID, SERIAL))

        assertTrue(response.deleted)
        verify(exactly = 0) { manager.deleteOnu(any()) }
    }

    @Test
    fun `missing reservation does not silently succeed while ONU remains configured`() {
        every { ownerships.find(SERIAL) } returns null
        every { ownerships.requireOwner(SERIAL, OPERATION_ID) } throws ResponseStatusException(HttpStatus.NOT_FOUND)
        every { manager.externalIdBySn(SERIAL) } returns "olt_1_1_3"

        val failure = assertThrows(ResponseStatusException::class.java) {
            controller.compensate(OnuV2CompensateRequest(OPERATION_ID, SERIAL))
        }

        assertEquals(HttpStatus.CONFLICT, failure.status)
        verify(exactly = 0) { manager.deleteOnu(any()) }
    }

    @Test
    fun `missing reservation deletes ONU only when persisted operation evidence matches`() {
        every { ownerships.find(SERIAL) } returns null
        every { ownerships.requireOwner(SERIAL, OPERATION_ID) } throws ResponseStatusException(HttpStatus.NOT_FOUND)
        every { manager.externalIdBySn(SERIAL) } returnsMany listOf("olt_1_1_3", null)
        every { manager.deleteOnu("olt_1_1_3") } returns SmartOltActionResponseDto()

        val response = controller.compensate(requestWithExpectedExternalId("olt_1_1_3"))

        assertTrue(response.deleted)
        assertEquals("olt_1_1_3", response.externalId)
        verify(exactly = 1) { manager.deleteOnu("olt_1_1_3") }
    }

    @Test
    fun `missing reservation rejects configured ONU when evidence does not match`() {
        every { ownerships.find(SERIAL) } returns null
        every { ownerships.requireOwner(SERIAL, OPERATION_ID) } throws ResponseStatusException(HttpStatus.NOT_FOUND)
        every { manager.externalIdBySn(SERIAL) } returns "olt_1_1_3"

        val failure = assertThrows(ResponseStatusException::class.java) {
            controller.compensate(requestWithExpectedExternalId("different_external_id"))
        }

        assertEquals(HttpStatus.CONFLICT, failure.status)
        verify(exactly = 0) { manager.deleteOnu(any()) }
    }

    @Test
    fun `configured ONU already absent releases its matching reservation idempotently`() {
        val ownership = ownership(externalId = "olt_1_1_3")
        every { ownerships.find(SERIAL) } returns ownership
        every { ownerships.requireOwner(SERIAL, OPERATION_ID) } returns ownership
        every { ownerships.releaseAfterConfirmedCleanup(ownership) } returns Unit
        every { manager.externalIdBySn(SERIAL) } returns null

        val response = controller.compensate(requestWithExpectedExternalId("olt_1_1_3"))

        assertTrue(response.deleted)
        verify(exactly = 1) { ownerships.releaseAfterConfirmedCleanup(ownership) }
        verify(exactly = 0) { manager.deleteOnu(any()) }
    }

    @Test
    fun `owned configured ONU rejects an external identity that differs from operation evidence`() {
        val ownership = ownership(externalId = "olt_1_1_3")
        every { ownerships.find(SERIAL) } returns ownership
        every { ownerships.requireOwner(SERIAL, OPERATION_ID) } returns ownership
        every { manager.externalIdBySn(SERIAL) } returns "olt_1_1_3"

        val failure = assertThrows(ResponseStatusException::class.java) {
            controller.compensate(requestWithExpectedExternalId("different_external_id"))
        }

        assertEquals(HttpStatus.CONFLICT, failure.status)
        verify(exactly = 0) { manager.deleteOnu(any()) }
        verify(exactly = 0) { ownerships.releaseAfterConfirmedCleanup(any()) }
    }

    @Test
    fun `failed inventory confirmation keeps compensation unconfirmed`() {
        every { ownerships.find(SERIAL) } returns null
        every { ownerships.requireOwner(SERIAL, OPERATION_ID) } throws ResponseStatusException(HttpStatus.NOT_FOUND)
        every { manager.externalIdBySn(SERIAL) } returnsMany listOf("olt_1_1_3", "olt_1_1_3")
        every { manager.deleteOnu("olt_1_1_3") } returns SmartOltActionResponseDto(status = true)

        val failure = assertThrows(ResponseStatusException::class.java) {
            controller.compensate(requestWithExpectedExternalId("olt_1_1_3"))
        }

        assertEquals(HttpStatus.BAD_GATEWAY, failure.status)
    }

    private fun requestWithExpectedExternalId(externalId: String): OnuV2CompensateRequest =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(
                """{"operationId":"$OPERATION_ID","sn":"$SERIAL","expectedExternalId":"$externalId"}""",
                OnuV2CompensateRequest::class.java,
            )

    private fun ownership(externalId: String?) = ProvisioningV2OnuOwnership(
        serial = SERIAL,
        operationId = OPERATION_ID,
        callerEnv = "unknown",
        requestHash = "request-hash",
        externalId = externalId,
        board = 1,
        port = 3,
        ontId = 7,
        stage = "AUTHORIZED",
    )

    private companion object {
        const val OPERATION_ID = "cancel-op-1"
        const val SERIAL = "ZTEGDC47BFFD"
    }
}
