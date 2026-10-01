package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FiberRegistrationServiceTest {
    private val registration = mockk<ProvisioningV2RegistrationService>(relaxed = true)
    private val service = FiberRegistrationService(registration)

    private fun request(type: InstallationType, operationId: String?) = SubscriptionRequest(
        firstName = "A", lastName = "B", dni = "1", address = "x", phone = "9", subscriptionDate = 1L, planId = 1,
        additionalDeviceIds = emptyList(), placeId = 1, location = GeoLocation(0.0, 0.0), technicianId = 1,
        hostDeviceId = 1, installationType = type, registrationOperationId = operationId,
    )

    @Test
    fun `prepares only requests linked to a preauthorization`() {
        service.prepare(71, request(InstallationType.FIBER, "op-1"))
        service.prepare(71, request(InstallationType.WIRELESS, null))

        verify(exactly = 1) { registration.prepareSubmission(71, any()) }
    }

    @Test
    fun `promotes fiber registrations and leaves other installation types to the legacy path`() {
        val operation = mockk<ProvisioningOperation>(relaxed = true)
        every { registration.start(any(), any(), any()) } returns operation

        assertTrue(service.handles(request(InstallationType.FIBER, "op-1")))
        assertFalse(service.handles(request(InstallationType.ONLY_TV_FIBER, null)))
        assertEquals(operation, service.promote(Subscription(id = 9, equipmentCondition = com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition.LOAN), request(InstallationType.FIBER, "op-1"), 71))
    }
}
