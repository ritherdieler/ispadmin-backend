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
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIpConflictNocNotifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile

class SubscriptionControllerMultipartIdempotencyTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val subscriptionService = mockk<SubscriptionService>()
    private val storageService = mockk<FirebaseStorageService>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val integrityViolationClassifier = SubscriptionIntegrityViolationClassifier()
    private val ipConflictNocNotifier = mockk<SubscriptionIpConflictNocNotifier>(relaxed = true)
    private val gatewayCpe = mockk<org.springframework.beans.factory.ObjectProvider<com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient>>()

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
        integrityViolationClassifier = integrityViolationClassifier,
        ipConflictNocNotifier = ipConflictNocNotifier,
        subscriptionProvisionService = mockk(relaxed = true),
        gatewayCpe = gatewayCpe,
    )

    @BeforeEach
    fun stubGateway() {
        every { gatewayCpe.ifAvailable } returns null
    }

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

    @Test
    fun `newSubcriptionWithFacade returns IP_CONFLICT and alerts NOC on ip unique violation`() {
        every {
            subscriptionService.findExistingSubscriptionByClientRequestId(any())
        } returns null
        every { storageService.uploadFileToFolder(any(), any()) } returns "https://facade/1.jpg"
        every {
            subscriptionService.registerSubscription(any(), any())
        } throws DataIntegrityViolationException(
            "could not execute statement",
            RuntimeException("Duplicate entry '192.168.1.77' for key 'subscription.ip'")
        )

        val request = sampleRequest(
            clientRequestId = "offline-req-ip",
            clientIpAddress = "192.168.1.77"
        )
        val response = controller.newSubcriptionWithFacade(
            newSubscription = request,
            facadephoto = MockMultipartFile(
                "facadePhoto",
                "facade.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                byteArrayOf(1, 2, 3),
            )
        )

        assertEquals(409, response.status)
        assertEquals(SubscriptionIpConflictNocNotifier.ERROR_CODE, response.errorCode)
        assertEquals(SubscriptionIpConflictNocNotifier.ERROR_MESSAGE, response.error)
        verify(exactly = 1) { ipConflictNocNotifier.notifyIpConflict(request) }
    }

    @Test
    fun `newSubcriptionWithFacade keeps generic 409 without NOC when dni conflicts`() {
        every {
            subscriptionService.findExistingSubscriptionByClientRequestId(any())
        } returns null
        every { storageService.uploadFileToFolder(any(), any()) } returns "https://facade/1.jpg"
        every {
            subscriptionService.registerSubscription(any(), any())
        } throws DataIntegrityViolationException(
            "could not execute statement",
            RuntimeException("Duplicate entry '12345678' for key 'subscription.dni'")
        )

        val response = controller.newSubcriptionWithFacade(
            newSubscription = sampleRequest(clientRequestId = "offline-req-dni"),
            facadephoto = MockMultipartFile(
                "facadePhoto",
                "facade.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                byteArrayOf(1, 2, 3),
            )
        )

        assertEquals(409, response.status)
        assertNull(response.errorCode)
        assertEquals("Este usuario no se encuentra registrado", response.error)
        verify(exactly = 0) { ipConflictNocNotifier.notifyIpConflict(any()) }
    }

    @Test
    fun `newSubscription returns IP_CONFLICT and alerts NOC on ip unique violation`() {
        every {
            subscriptionService.registerSubscription(any(), any())
        } throws DataIntegrityViolationException(
            "could not execute statement",
            RuntimeException("Duplicate entry '10.0.0.15' for key 'subscription.ip'")
        )

        val request = sampleRequest(clientIpAddress = "10.0.0.15")
        val response = controller.newSubscription(request)

        assertEquals(409, response.status)
        assertEquals(SubscriptionIpConflictNocNotifier.ERROR_CODE, response.errorCode)
        verify(exactly = 1) { ipConflictNocNotifier.notifyIpConflict(request) }
    }

    private fun sampleRequest(
        clientRequestId: String? = null,
        clientIpAddress: String? = null
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
        hostDeviceId = 1,
        installationType = InstallationType.WIRELESS,
        equipmentCondition = EquipmentCondition.LOAN,
        clientRequestId = clientRequestId,
        clientIpAddress = clientIpAddress
    )
}
