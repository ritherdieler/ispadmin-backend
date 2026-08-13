package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.CouponRepository
import com.dscorp.wispadmin.wispadmin.repository.NapBoxRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile

class SubscriptionControllerMultipartIdempotencyTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val subscriptionService = mockk<SubscriptionService>()
    private val storageService = mockk<FirebaseStorageService>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val controller = SubscriptionController(
        repository = subscriptionRepository,
        subscriptionService = subscriptionService,
        placeRepository = mockk(relaxed = true),
        planRepository = mockk(relaxed = true),
        napBoxRepository = mockk(relaxed = true),
        networkDeviceRepository = mockk(relaxed = true),
        couponRepository = mockk(relaxed = true),
        subscriptionLogRepository = mockk(relaxed = true),
        storageService = storageService,
        eventPublisher = eventPublisher,
    )

    @Test
    fun `newSubcriptionWithFacade skips photo upload when clientRequestId already exists`() {
        val existing = Subscription(
            firstName = "Juan",
            lastName = "Perez",
            dni = "12345678",
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply {
            id = 42
            clientRequestId = "offline-req-42"
        }

        every {
            subscriptionService.findExistingSubscriptionByClientRequestId("offline-req-42")
        } returns existing
        every {
            subscriptionService.registerSubscription(any(), any())
        } returns existing.toDto().copy(alreadyRegistered = true)

        val photoPart = MockMultipartFile(
            "facadePhoto",
            "facade.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            byteArrayOf(1, 2, 3),
        )

        val response = controller.newSubcriptionWithFacade(
            newSubscription = sampleRequest(clientRequestId = "offline-req-42"),
            facadephoto = photoPart,
        )

        assertEquals(200, response.status)
        val data = response.data as com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
        assertEquals(42, data.id)
        assertTrue(data.alreadyRegistered)

        verify(exactly = 0) { storageService.uploadFileToFolder(any(), any()) }
        verify(exactly = 1) { subscriptionService.registerSubscription(any(), any()) }
    }

    private fun sampleRequest(clientRequestId: String) = SubscriptionRequest(
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
        installationType = InstallationType.WIRELESS,
        clientRequestId = clientRequestId,
    )
}
