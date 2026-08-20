package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.IpPoolRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.subscription.SubscriptionRegisteredEvent
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.IInstallationStrategy
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationResult
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationStrategyFactory
import com.dscorp.wispadmin.wispadmin.service.validators.ISubscriptionValidator
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class SubscriptionServiceIdempotencyTest {

    private val repository = mockk<SubscriptionRepository>()
    private val ipPoolRepository = mockk<IpPoolRepository>()
    private val networkDeviceRepository = mockk<NetworkDeviceRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val placeRepository = mockk<PlaceRepository>()
    private val subscriptionValidator = mockk<ISubscriptionValidator>(relaxed = true)
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val installationStrategyFactory = mockk<InstallationStrategyFactory>()
    private val installationStrategy = mockk<IInstallationStrategy>()
    private val errorLogRepository = mockk<com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository>(relaxed = true)
    private val subscriptionProvisionService by lazy {
        SubscriptionProvisionService(
            repository = repository,
            networkDeviceRepository = networkDeviceRepository,
            planRepository = planRepository,
            placeRepository = placeRepository,
            installationStrategyFactory = installationStrategyFactory,
            errorLogRepository = errorLogRepository,
            genieAcsProperties = com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsProperties().apply {
                enabled = false
            },
        )
    }

    private val service by lazy {
        SubscriptionService(
        repository = repository,
        ipPoolRepository = ipPoolRepository,
        networkDeviceRepository = networkDeviceRepository,
        planRepository = planRepository,
        placeRepository = placeRepository,
        napBoxRepository = mockk(relaxed = true),
        onuService = mockk(relaxed = true),
        onuRepository = mockk(relaxed = true),
        installationOrderRepository = mockk(relaxed = true),
        notificationService = mockk(relaxed = true),
        subscriptionLogRepository = mockk(relaxed = true),
        errorLogRepository = errorLogRepository,
        borneManagementService = mockk(relaxed = true),
        mikrotikService = mockk(relaxed = true),
        queueManager = mockk(relaxed = true),
        addressListManager = mockk(relaxed = true),
        serviceCutManager = mockk(relaxed = true),
        serviceReactivationManager = mockk(relaxed = true),
        subscriptionValidator = subscriptionValidator,
        paymentRepository = mockk(relaxed = true),
        installationStrategyFactory = installationStrategyFactory,
        applicationEventPublisher = applicationEventPublisher,
        cancelledOnuReuseService = mockk(relaxed = true),
        subscriptionProvisionService = subscriptionProvisionService,
        )
    }

    @Test
    fun `registerSubscription returns existing dto when clientRequestId already exists`() {
        val existing = Subscription(
            firstName = "Juan",
            lastName = "Perez",
            dni = "12345678",
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply {
            id = 99
            clientRequestId = "offline-req-1"
            installationType = InstallationType.WIRELESS
        }

        stubLookupsForProvision()
        every { repository.findByClientRequestId("offline-req-1") } returns Optional.of(existing)
        every { repository.save(any()) } answers { firstArg() }
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } returns InstallationResult(queueAdded = true)

        val result = service.registerSubscription(
            newSubscription = sampleRequest(clientRequestId = "offline-req-1"),
            onSuccess = { throw AssertionError("onSuccess must not run for duplicate clientRequestId") },
        )

        assertEquals(99, result.id)
        assertTrue(result.alreadyRegistered)
        verify(exactly = 0) { subscriptionValidator.validateSubscriptionRequest(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<SubscriptionRegisteredEvent>()) }
        verify(exactly = 1) { installationStrategy.processInstallation(existing, any(), any(), any(), any()) }
    }

    @Test
    fun `registerSubscription keeps saved subscription when MikroTik handshake fails`() {
        stubLookupsForProvision()
        every { repository.findByClientRequestId(any()) } returns Optional.empty()
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(
            IpPool(id = 1, ipSegment = "192.168.1.0/24")
        )
        every { repository.save(any()) } answers {
            firstArg<Subscription>().apply { id = 77 }
        }
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } throws MikrotikCommandException("rest PUT /rest/queue/simple: Remote host terminated the handshake")

        val result = service.registerSubscription(
            newSubscription = sampleRequest(clientRequestId = "offline-req-new"),
            onSuccess = { },
        )

        assertEquals(77, result.id)
        assertFalse(result.alreadyRegistered)
        assertTrue(result.provisioningPending)
        assertEquals(
            com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus.PENDING,
            result.mikrotikProvisionStatus
        )
        verify(atLeast = 1) { repository.save(any()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<SubscriptionRegisteredEvent>()) }
    }

    @Test
    fun `registerSubscription keeps saved fiber when OLT fails but MikroTik succeeds`() {
        stubLookupsForProvision()
        every { repository.findByClientRequestId(any()) } returns Optional.empty()
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(
            IpPool(id = 1, ipSegment = "192.168.1.0/24")
        )
        every { repository.save(any()) } answers {
            firstArg<Subscription>().apply { id = 78 }
        }
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } returns InstallationResult(
            queueAdded = true,
            onuAuthorized = false,
            oltError = "OLT unreachable"
        )

        val result = service.registerSubscription(
            newSubscription = sampleRequest(clientRequestId = "fiber-olt-down").apply {
                installationType = InstallationType.FIBER
                napBoxId = 1
                onu = com.dscorp.wispadmin.wispadmin.dto.OnuDto(
                    sn = "ONU123",
                    olt_id = "1",
                    board = "1",
                    port = "1",
                    onu_type_name = "HG8310"
                )
            },
            onSuccess = { },
        )

        assertEquals(78, result.id)
        assertTrue(result.provisioningPending)
        assertEquals(
            com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus.COMPLETE,
            result.mikrotikProvisionStatus
        )
        assertEquals(
            com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus.PENDING,
            result.oltProvisionStatus
        )
        assertEquals(
            com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus.NA,
            result.tr069ProvisionStatus
        )
    }

    @Test
    fun `registerSubscription fiber with genieacs disabled marks tr069 as NA`() {
        stubLookupsForProvision()
        every { repository.findByClientRequestId(any()) } returns Optional.empty()
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(
            IpPool(id = 1, ipSegment = "192.168.1.0/24")
        )
        every { repository.save(any()) } answers {
            firstArg<Subscription>().apply { id = 79 }
        }
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } returns InstallationResult(queueAdded = true, onuAuthorized = true)

        val result = service.registerSubscription(
            newSubscription = sampleRequest(clientRequestId = "fiber-tr069-off").apply {
                installationType = InstallationType.FIBER
                napBoxId = 1
                wifiSsid24 = "casa24"
                wifiPassword24 = "password1"
                wifiSsid5 = "casa5"
                wifiPassword5 = "password1"
                onu = com.dscorp.wispadmin.wispadmin.dto.OnuDto(
                    sn = "VSOL0031C0B6",
                    olt_id = "1",
                    board = "1",
                    port = "1",
                    onu_type_name = "V2804AX15T"
                )
            },
            onSuccess = { },
        )

        assertEquals(79, result.id)
        assertEquals(
            com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus.NA,
            result.tr069ProvisionStatus
        )
        assertFalse(result.tr069RequiresManualConfig)
    }

    @Test
    fun `registerSubscription with existing clientRequestId reconciles pending provision`() {
        val existing = Subscription(
            firstName = "Juan",
            lastName = "Perez",
            dni = "12345678",
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply {
            id = 55
            clientRequestId = "offline-pending"
            installationType = InstallationType.WIRELESS
            mikrotikProvisionStatus =
                com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus.PENDING
            oltProvisionStatus = com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus.NA
            hostDevice = NetworkDevice(id = 1)
            plan = Plan(id = 1)
            place = Place(id = 1)
        }

        stubLookupsForProvision()
        every { repository.findByClientRequestId("offline-pending") } returns Optional.of(existing)
        every { repository.save(any()) } answers { firstArg() }
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } returns InstallationResult(queueAdded = true)

        val result = service.registerSubscription(
            newSubscription = sampleRequest(clientRequestId = "offline-pending"),
            onSuccess = { throw AssertionError("onSuccess must not run for duplicate") },
        )

        assertTrue(result.alreadyRegistered)
        assertFalse(result.provisioningPending)
        assertEquals(
            com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus.COMPLETE,
            result.mikrotikProvisionStatus
        )
    }

    @Test
    fun `findExistingSubscriptionByClientRequestId returns null for blank clientRequestId`() {
        val result = service.findExistingSubscriptionByClientRequestId("   ")

        assertEquals(null, result)
        verify(exactly = 0) { repository.findByClientRequestId(any()) }
    }

    private fun stubLookupsForProvision() {
        every { installationStrategyFactory.getStrategy(any()) } returns installationStrategy
        every { networkDeviceRepository.findById(1) } returns Optional.of(NetworkDevice(id = 1, name = "MK1"))
        every { planRepository.findById(1) } returns Optional.of(Plan(id = 1, name = "f50", downloadSpeed = 50, uploadSpeed = 50))
        every { placeRepository.findById(1) } returns Optional.of(Place(id = 1, name = "Huacho"))
        every { errorLogRepository.save(any()) } answers { firstArg() }
    }

    private fun sampleRequest(clientRequestId: String?) = SubscriptionRequest(
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

    @Test
    fun `registerSubscription uses clientIpAddress for saved ip instead of auto assigned`() {
        stubLookupsForProvision()
        val saved = slot<Subscription>()
        val provisioned = slot<Subscription>()
        every { repository.findByClientRequestId(any()) } returns Optional.empty()
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(
            IpPool(id = 1, ipSegment = "192.168.1.0/24")
        )
        every { repository.save(capture(saved)) } answers {
            firstArg<Subscription>().apply { id = 88 }
        }
        every {
            installationStrategy.processInstallation(capture(provisioned), any(), any(), any(), any())
        } returns InstallationResult(queueAdded = true)

        val result = service.registerSubscription(
            newSubscription = sampleRequest(clientRequestId = "offline-ip-1").apply {
                clientIpAddress = "192.168.1.77"
            },
            onSuccess = { },
        )

        assertEquals(88, result.id)
        assertEquals("192.168.1.77", saved.captured.ip)
        assertEquals("192.168.1.77", result.ip)
        assertEquals("192.168.1.77", provisioned.captured.ip)
    }
}
