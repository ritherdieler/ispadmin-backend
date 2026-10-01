package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.PppoeProperties
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrder
import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrderStatus
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import com.dscorp.wispadmin.wispadmin.repository.InstallationOrderRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningOperation
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningV2RegistrationService
import com.dscorp.wispadmin.wispadmin.service.subscription.SubscriptionRegisteredEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class SubscriptionServiceFiberRegistrationTest {

    private val repository = mockk<SubscriptionRepository>()
    private val networkDeviceRepository = mockk<NetworkDeviceRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val placeRepository = mockk<PlaceRepository>()
    private val installationOrderRepository = mockk<InstallationOrderRepository>(relaxed = true)
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val registration = mockk<ProvisioningV2RegistrationService>(relaxed = true)

    private val service = SubscriptionService(
        repository = repository,
        ipPoolRepository = mockk(relaxed = true),
        networkDeviceRepository = networkDeviceRepository,
        planRepository = planRepository,
        placeRepository = placeRepository,
        napBoxRepository = mockk(relaxed = true),
        onuService = mockk(relaxed = true),
        installationOrderRepository = installationOrderRepository,
        notificationService = mockk(relaxed = true),
        subscriptionLogRepository = mockk(relaxed = true),
        errorLogRepository = mockk<com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository>().also {
            every { it.save(any()) } answers { firstArg() }
        },
        borneManagementService = mockk(relaxed = true),
        mikrotikService = mockk(relaxed = true),
        queueManager = mockk(relaxed = true),
        addressListManager = mockk(relaxed = true),
        serviceCutManager = mockk(relaxed = true),
        serviceReactivationManager = mockk(relaxed = true),
        subscriptionValidator = mockk(relaxed = true),
        paymentRepository = mockk(relaxed = true),
        installationStrategyFactory = mockk(),
        applicationEventPublisher = applicationEventPublisher,
        cancelledOnuReuseService = mockk(relaxed = true),
        fiberOnuSnClaimService = mockk(relaxed = true),
        subscriptionProvisionService = mockk(relaxed = true),
        ipAllocationService = mockk(relaxed = true),
        pppoeProperties = PppoeProperties(),
        pppoeSecretCipher = mockk(relaxed = true),
        pppoeAccessService = mockk(relaxed = true),
        fiberRegistrationService = com.dscorp.wispadmin.wispadmin.service.provisioningv2.FiberRegistrationService(registration),
    )

    @Test
    fun `fiber registration closes the business cycle like wireless`() {
        val order = InstallationOrder(id = 7, status = InstallationOrderStatus.EN_CURSO)
        every { repository.findByClientRequestId(any()) } returns Optional.empty()
        every { repository.save(any()) } answers { firstArg<Subscription>().apply { id = 501 } }
        every { networkDeviceRepository.findById(1) } returns Optional.of(NetworkDevice(id = 1, name = "MK2"))
        every { planRepository.findById(1) } returns Optional.of(Plan(id = 1, name = "f100"))
        every { placeRepository.findById(1) } returns Optional.of(Place(id = 1, name = "Huacho"))
        every { installationOrderRepository.findById(7) } returns Optional.of(order)
        every { installationOrderRepository.save(any()) } answers { firstArg() }
        every { registration.start(any(), any(), any()) } returns mockk<ProvisioningOperation>(relaxed = true)
        val registered = slot<Subscription>()
        var onSuccessCalls = 0

        val result = service.registerSubscription(
            newSubscription = fiberRequest(),
            onSuccess = { onSuccessCalls++; registered.captured = it },
            authenticatedOperatorId = 71,
        )

        assertEquals(501, result.id)
        assertEquals(1, onSuccessCalls)
        assertEquals(InstallationOrderStatus.CERRADO, order.status)
        verify(exactly = 1) { installationOrderRepository.save(order) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(SubscriptionRegisteredEvent(501)) }
        assertTrue(registered.isCaptured)
    }

    private fun fiberRequest() = SubscriptionRequest(
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
        installationType = InstallationType.FIBER,
        napBoxId = 1,
        vlan = "100",
        onu = OnuDto(olt_id = "olt-1", pon_type = "GPON", board = "0", port = "1", onu_type_name = "VSOLVA74", sn = "VSOL0031C0B6"),
        registrationOperationId = "op-1",
        installationOrderId = 7,
    )
}
