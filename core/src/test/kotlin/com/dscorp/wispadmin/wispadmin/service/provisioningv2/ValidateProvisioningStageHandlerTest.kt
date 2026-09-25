package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ValidateProvisioningStageHandlerTest {
    private val repository = mockk<SubscriptionRepository>()
    private val operation = ProvisioningOperation("op", "staging", 42, "HWTC9F4BF950")

    @Test fun `accepts the durable ONU identity and all v2 preconditions`() {
        every { repository.lockIdentityOwner(42) } returns validSubscription()
        val handler = ValidateProvisioningStageHandler(repository)

        assertEquals(StageObservation.SATISFIED, handler.reconcile(context()))
        assertEquals(StageObservation.SATISFIED, handler.apply(context()))
        assertEquals(StageObservation.SATISFIED, handler.compensate(context()))
    }

    @Test fun `fails permanently when subscription ONU identity changed`() {
        every { repository.lockIdentityOwner(42) } returns validSubscription().apply { fiberOnuSn = "ZTEGDC47BFFD" }

        val error = assertThrows(ProvisioningStepException::class.java) {
            ValidateProvisioningStageHandler(repository).reconcile(context())
        }

        assertEquals("ONU_IDENTITY_MISMATCH", error.failure.code)
        assertEquals(false, error.failure.retryable)
    }

    private fun context() = ProvisioningStageContext(operation, {}, { _, _ -> }, { null })

    private fun validSubscription() = Subscription(id = 42, equipmentCondition = EquipmentCondition.LOAN).apply {
        fiberOnuSn = operation.serial
        accessMode = AccessMode.PPPOE_DYNAMIC
        pppoeUsername = "gf42"
        pppoePasswordEnc = "enc:v1:placeholder"
        plan = Plan(id = 1, name = "F200", downloadSpeed = 200, uploadSpeed = 200)
        hostDevice = NetworkDevice(id = 8, name = "MK2")
    }
}
