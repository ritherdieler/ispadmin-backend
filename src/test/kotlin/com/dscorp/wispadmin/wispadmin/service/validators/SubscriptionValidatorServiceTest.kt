package com.dscorp.wispadmin.wispadmin.service.validators

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SubscriptionValidatorServiceTest {

    private val service = SubscriptionValidatorService(mockk(relaxed = true))

    @Test
    fun `FIBER exige vlan 1 o 100`() {
        assertDoesNotThrow {
            service.validateSubscriptionRequest(fiberRequest(vlan = "1"))
        }
        assertDoesNotThrow {
            service.validateSubscriptionRequest(fiberRequest(vlan = "100"))
        }
    }

    @Test
    fun `FIBER falla sin vlan`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.validateSubscriptionRequest(fiberRequest(vlan = null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.validateSubscriptionRequest(fiberRequest(vlan = "  "))
        }
    }

    @Test
    fun `FIBER falla con vlan invalida`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.validateSubscriptionRequest(fiberRequest(vlan = "50"))
        }
    }

    @Test
    fun `WIRELESS no exige vlan`() {
        assertDoesNotThrow {
            service.validateSubscriptionRequest(
                fiberRequest(vlan = null).copy(
                    installationType = InstallationType.WIRELESS,
                    onu = null,
                    napBoxId = null,
                )
            )
        }
    }

    private fun fiberRequest(vlan: String?) = SubscriptionRequest(
        firstName = "Juan",
        lastName = "Perez",
        dni = "12345678",
        address = "Calle 1",
        phone = "999888777",
        subscriptionDate = System.currentTimeMillis(),
        planId = 1,
        additionalDeviceIds = emptyList(),
        placeId = 1,
        location = GeoLocation(-11.0, -77.0),
        technicianId = 1,
        hostDeviceId = 1,
        napBoxId = 1,
        installationType = InstallationType.FIBER,
        equipmentCondition = EquipmentCondition.LOAN,
        vlan = vlan,
        onu = OnuDto(sn = "VSOL0031C0B6", onu_type_name = "VSOLVA74"),
    )
}
