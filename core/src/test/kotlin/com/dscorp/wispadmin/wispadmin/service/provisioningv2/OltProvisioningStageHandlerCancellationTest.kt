package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuV2CompensateResponse
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OltProvisioningStageHandlerCancellationTest {
    private val gateway = mockk<GatewayOnuActivationClient>()
    private val handler = OltProvisioningStageHandler(
        subscriptions = mockk<SubscriptionRepository>(),
        gateway = gateway,
        json = jacksonObjectMapper().findAndRegisterModules(),
    )

    @Test
    fun `compensation sends the ONU identity captured by the operation`() {
        val operation = ProvisioningOperation(
            id = "cancel-operation-1",
            environment = "stg",
            subscriptionId = 84,
            serial = "ZTEGDC47BFFD",
            state = ProvisioningState.CANCEL_REQUESTED,
            oltEvidence = OltProvisioningResource("olt_1_1_3", board = 1, port = 3, ontId = 7),
        )
        every { gateway.compensateV2(any()) } returns GatewayOnuV2CompensateResponse(
            externalId = "olt_1_1_3",
            deleted = true,
        )

        val observation = handler.compensate(context(operation))

        assertEquals(StageObservation.SATISFIED, observation)
        verify(exactly = 1) {
            gateway.compensateV2(match {
                it.operationId == operation.id &&
                    it.sn == operation.serial &&
                    it.expectedExternalId == "olt_1_1_3"
            })
        }
    }

    @Test
    fun `compensation remains unsuccessful unless the gateway confirms deletion`() {
        val operation = ProvisioningOperation(
            id = "cancel-operation-2",
            environment = "stg",
            subscriptionId = 84,
            serial = "ZTEGDC47BFFD",
            state = ProvisioningState.CANCEL_REQUESTED,
        )
        every { gateway.compensateV2(any()) } returns GatewayOnuV2CompensateResponse(deleted = false)

        val failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            handler.compensate(context(operation))
        }

        assertEquals("OLT_DELETE_UNCONFIRMED", failure.message)
    }

    private fun context(operation: ProvisioningOperation) = ProvisioningStageContext(
        operation = operation,
        leaseAssertion = {},
        resourceCapture = { _, _ -> },
        resourceSnapshot = { null },
    )
}
