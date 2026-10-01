package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class SubscriptionControllerConcurrentDuplicateTest {

    private val service = mockk<SubscriptionService>()
    private val controller = SubscriptionController(
        repository = mockk(relaxed = true),
        subscriptionService = service,
        placeRepository = mockk(relaxed = true),
        planRepository = mockk(relaxed = true),
        napBoxRepository = mockk(relaxed = true),
        networkDeviceRepository = mockk(relaxed = true),
        couponRepository = mockk(relaxed = true),
        subscriptionLogRepository = mockk(relaxed = true),
        storageService = mockk(relaxed = true),
        eventPublisher = mockk(relaxed = true),
        integrityViolationClassifier = SubscriptionIntegrityViolationClassifier(),
        ipConflictNocNotifier = mockk(relaxed = true),
        subscriptionProvisionService = mockk(relaxed = true),
        gatewayCpe = mockk(relaxed = true),
        subscriptionAcsLinkService = mockk(relaxed = true),
    )

    @Test
    fun `concurrent duplicate clientRequestId returns the subscription registered by the winner`() {
        every { service.registerSubscription(any(), any(), any()) } throws DataIntegrityViolationException(
            "Duplicate entry 'op-1' for key 'subscription.UK_2a8chskmj8lhn0s4k8nng1a5x' client_request_id"
        )
        every { service.findRegisteredByClientRequestId("op-1") } returns SubscriptionDto(id = 77, alreadyRegistered = true)

        val response = controller.newSubscription(request("op-1"))

        assertEquals(200, response.status)
        val data = response.data as SubscriptionDto
        assertEquals(77, data.id)
        assertTrue(data.alreadyRegistered)
    }

    @Test
    fun `dni conflict keeps the conflict response`() {
        every { service.registerSubscription(any(), any(), any()) } throws DataIntegrityViolationException(
            "Duplicate entry '12345678' for key 'subscription.dni'"
        )

        val response = controller.newSubscription(request("op-2"))

        assertEquals(409, response.status)
        assertEquals("DNI_CONFLICT", response.errorCode)
    }

    @Test
    fun `dni conflict is reported with a real HTTP 409 status`() {
        every { service.registerSubscription(any(), any(), any()) } throws DataIntegrityViolationException(
            "Duplicate entry '12345678' for key 'subscription.dni'"
        )
        val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build()
        val body = """{"firstName":"A","lastName":"B","dni":"12345678","installationType":"WIRELESS","planId":1,
            |"placeId":1,"hostDeviceId":1,"subscriptionDate":1,"additionalDeviceIds":[],"address":"x","phone":"9",
            |"technicianId":1,"location":{"latitude":-12.0,"longitude":-77.0}}""".trimMargin()

        val result = mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/subscription")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)
        ).andReturn()

        assertEquals(409, result.response.status)
        assertTrue(result.response.contentAsString.contains("DNI_CONFLICT"))
    }

    private fun request(clientRequestId: String) = SubscriptionRequest(
        firstName = "Juan", lastName = "Perez", dni = "12345678", address = "Calle 1", phone = "999888777",
        subscriptionDate = 1L, planId = 1, additionalDeviceIds = emptyList(), placeId = 1,
        location = GeoLocation(-11.0, -77.0), technicianId = 1, hostDeviceId = 1,
        installationType = InstallationType.FIBER, clientRequestId = clientRequestId,
    )
}
