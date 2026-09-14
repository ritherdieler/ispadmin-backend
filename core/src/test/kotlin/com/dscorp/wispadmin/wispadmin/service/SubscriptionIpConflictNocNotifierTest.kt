package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.whatsapp.NamedTemplateParameter
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionIpConflictNocNotifierTest {

    private val whatsAppService = mockk<WhatsAppService>()

    @Test
    fun `sends noc template with ip conflict details when phone configured`() {
        val notifier = SubscriptionIpConflictNocNotifier(
            whatsAppService = whatsAppService,
            nocPhone = "51999888777",
            templateName = "noc_alert_v1",
            languageCode = "es"
        )
        val paramsSlot = slot<List<NamedTemplateParameter>>()
        every {
            whatsAppService.sendTemplateMessage(
                phoneNumber = any(),
                templateName = any(),
                languageCode = any(),
                parameters = capture(paramsSlot)
            )
        } returns true

        notifier.notifyIpConflict(
            request = sampleRequest(
                clientIpAddress = "192.168.1.77",
                clientRequestId = "offline-req-9"
            )
        )

        verify(exactly = 1) {
            whatsAppService.sendTemplateMessage(
                phoneNumber = "51999888777",
                templateName = "noc_alert_v1",
                languageCode = "es",
                parameters = any()
            )
        }
        val byName = paramsSlot.captured.associate { it.parameterName to it.text }
        assertEquals("HIGH", byName["severity"])
        assertTrue(byName["title"]!!.contains("Colision IP", ignoreCase = true))
        assertEquals("IP_CONFLICT", byName["reason_code"])
        assertTrue(byName["target"]!!.contains("192.168.1.77"))
    }

    @Test
    fun `skips send and does not throw when noc phone blank`() {
        val notifier = SubscriptionIpConflictNocNotifier(
            whatsAppService = whatsAppService,
            nocPhone = "  ",
            templateName = "noc_alert_v1",
            languageCode = "es"
        )

        notifier.notifyIpConflict(request = sampleRequest(clientIpAddress = "10.0.0.2"))

        verify(exactly = 0) {
            whatsAppService.sendTemplateMessage(any(), any(), any(), any())
        }
    }

    @Test
    fun `swallows whatsapp failures`() {
        val notifier = SubscriptionIpConflictNocNotifier(
            whatsAppService = whatsAppService,
            nocPhone = "51999888777",
            templateName = "noc_alert_v1",
            languageCode = "es"
        )
        every {
            whatsAppService.sendTemplateMessage(any(), any(), any(), any())
        } throws RuntimeException("meta down")

        notifier.notifyIpConflict(request = sampleRequest(clientIpAddress = "10.0.0.3"))
    }

    private fun sampleRequest(
        clientIpAddress: String?,
        clientRequestId: String? = null
    ) = SubscriptionRequest(
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
        hostDeviceId = 10,
        installationType = InstallationType.WIRELESS,
        equipmentCondition = EquipmentCondition.LOAN,
        clientRequestId = clientRequestId,
        clientIpAddress = clientIpAddress
    )
}
