package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.PppoeProperties
import com.dscorp.wispadmin.wispadmin.observability.ObservabilityReporter
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.IpPoolRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeAccessService
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.IInstallationStrategy
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationResult
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationStrategyFactory
import com.dscorp.wispadmin.wispadmin.service.validators.ISubscriptionValidator
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class SubscriptionServiceTest {

    private val repository = mockk<SubscriptionRepository>()
    private val ipPoolRepository = mockk<IpPoolRepository>()
    private val networkDeviceRepository = mockk<NetworkDeviceRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val placeRepository = mockk<PlaceRepository>()
    private val subscriptionValidator = mockk<ISubscriptionValidator>(relaxed = true)
    private val installationStrategyFactory = mockk<InstallationStrategyFactory>()
    private val installationStrategy = mockk<IInstallationStrategy>()
    private val mikrotikService = mockk<IMikroTikService>(relaxed = true)
    private val pppoeAccessService = mockk<PppoeAccessService>(relaxed = true)
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val subscriptionLogRepository = mockk<SubscriptionLogRepository>(relaxed = true)
    private val borneManagementService = mockk<BorneManagementService>(relaxed = true)
    private val ipAllocationService = IpAllocationService(
        ipPoolRepository = ipPoolRepository,
        subscriptionRepository = repository,
        mikrotikService = mikrotikService,
        observabilityReporter = mockk<ObservabilityReporter>(relaxed = true)
    )

    private val service = SubscriptionService(
        repository = repository,
        ipPoolRepository = ipPoolRepository,
        networkDeviceRepository = networkDeviceRepository,
        planRepository = planRepository,
        placeRepository = placeRepository,
        napBoxRepository = mockk(relaxed = true),
        onuService = mockk(relaxed = true),
        installationOrderRepository = mockk(relaxed = true),
        notificationService = mockk(relaxed = true),
        subscriptionLogRepository = subscriptionLogRepository,
        errorLogRepository = mockk(relaxed = true),
        borneManagementService = borneManagementService,
        mikrotikService = mikrotikService,
        queueManager = mockk(relaxed = true),
        addressListManager = mockk(relaxed = true),
        serviceCutManager = mockk(relaxed = true),
        serviceReactivationManager = mockk(relaxed = true),
        subscriptionValidator = subscriptionValidator,
        paymentRepository = paymentRepository,
        installationStrategyFactory = installationStrategyFactory,
        fiberInstallationStrategy = mockk(relaxed = true),
        applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true),
        cancelledOnuReuseService = mockk(relaxed = true),
        subscriptionProvisionService = mockk(relaxed = true),
        ipAllocationService = ipAllocationService,
        pppoeProperties = PppoeProperties(),
        pppoeSecretCipher = mockk(relaxed = true),
        pppoeAccessService = pppoeAccessService,
    )

    @BeforeEach
    fun setUp() {
        every { repository.findActiveIps() } returns emptyList()
        every { repository.existsByIpAndServiceStatus(any(), any()) } returns false
        every { repository.findByIpAndServiceStatus(any(), any()) } returns emptyList()
    }

    @Test
    fun `getFreeIp assigns last plus one even when a lower hole exists`() {
        val pool = poolWithOctets((10..50) + (52..190))
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(pool)
        every { repository.findActiveIps() } returns pool.ips.mapNotNull { it.ip }

        val (ip, _) = service.getFreeIp()

        assertEquals("192.168.30.191", ip)
    }

    @Test
    fun `getFreeIp assigns 192_168_30_10 when eligible pool is empty`() {
        val pool = IpPool(id = 22, ipSegment = "192.168.30.0/24", isEligible = true)
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(pool)

        val (ip, _) = service.getFreeIp()

        assertEquals("192.168.30.10", ip)
    }

    @Test
    fun `getFreeIp falls back to lowest hole when last octet is 250`() {
        val pool = poolWithOctets((10..50) + (52..250))
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(pool)
        every { repository.findActiveIps() } returns pool.ips.mapNotNull { it.ip }

        val (ip, _) = service.getFreeIp()

        assertEquals("192.168.30.51", ip)
    }

    @Test
    fun `registerSubscription auto assigns 192_168_30_191 from eligible pool`() {
        val pool = poolWithOctets((10..50) + (52..190))
        val saved = slot<Subscription>()
        every { repository.findByClientRequestId(any()) } returns Optional.empty()
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(pool)
        every { repository.findActiveIps() } returns pool.ips.mapNotNull { it.ip }
        every { installationStrategyFactory.getStrategy(any()) } returns installationStrategy
        every { networkDeviceRepository.findById(1) } returns Optional.of(NetworkDevice(id = 1, name = "MK1"))
        every { planRepository.findById(1) } returns Optional.of(
            Plan(id = 1, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
        )
        every { placeRepository.findById(1) } returns Optional.of(Place(id = 1, name = "Huacho"))
        every { repository.save(capture(saved)) } answers {
            firstArg<Subscription>().apply { id = 191 }
        }
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } returns InstallationResult(queueAdded = true)

        val result = service.registerSubscription(
            newSubscription = SubscriptionRequest(
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
                clientRequestId = "auto-ip-191",
            ),
            onSuccess = { },
        )

        assertEquals("192.168.30.191", saved.captured.ip)
        assertEquals("192.168.30.191", result.ip)
    }

    @Test
    fun `registerSubscription does not force colliding preferred ip`() {
        val pool = IpPool(id = 22, ipSegment = "192.168.30.0/24", isEligible = true)
        val saved = slot<Subscription>()
        every { repository.findByClientRequestId(any()) } returns Optional.empty()
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(pool)
        every { repository.findActiveIps() } returns listOf("192.168.30.77")
        every { installationStrategyFactory.getStrategy(any()) } returns installationStrategy
        every { networkDeviceRepository.findById(1) } returns Optional.of(NetworkDevice(id = 1, name = "MK1"))
        every { planRepository.findById(1) } returns Optional.of(
            Plan(id = 1, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
        )
        every { placeRepository.findById(1) } returns Optional.of(Place(id = 1, name = "Huacho"))
        every { repository.save(capture(saved)) } answers {
            firstArg<Subscription>().apply { id = 192 }
        }
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } returns InstallationResult(queueAdded = true)

        val result = service.registerSubscription(
            newSubscription = SubscriptionRequest(
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
                clientRequestId = "auto-ip-skip-77",
                clientIpAddress = "192.168.30.77",
            ),
            onSuccess = { },
        )

        assertEquals("192.168.30.78", saved.captured.ip)
        assertEquals("192.168.30.78", result.ip)
    }

    @Test
    fun `registerSubscription persist guard retries allocate when ip became ACTIVE`() {
        val pool = IpPool(id = 22, ipSegment = "192.168.30.0/24", isEligible = true)
        val saved = slot<Subscription>()
        every { repository.findByClientRequestId(any()) } returns Optional.empty()
        every { ipPoolRepository.findAllEligiblePools() } returns listOf(pool)
        every { repository.existsByIpAndServiceStatus("192.168.30.10", ServiceStatus.ACTIVE) } returnsMany listOf(
            false,
            true,
            false
        )
        every { installationStrategyFactory.getStrategy(any()) } returns installationStrategy
        every { networkDeviceRepository.findById(1) } returns Optional.of(NetworkDevice(id = 1, name = "MK1"))
        every { planRepository.findById(1) } returns Optional.of(
            Plan(id = 1, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
        )
        every { placeRepository.findById(1) } returns Optional.of(Place(id = 1, name = "Huacho"))
        every { repository.save(capture(saved)) } answers {
            firstArg<Subscription>().apply { id = 193 }
        }
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } returns InstallationResult(queueAdded = true)

        val result = service.registerSubscription(
            newSubscription = SubscriptionRequest(
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
                clientRequestId = "persist-guard-1",
            ),
            onSuccess = { },
        )

        assertEquals("192.168.30.10", result.ip)
    }

    @Test
    fun `cancelService without IP cuts PPPoE instead of address list`() {
        val host = NetworkDevice(
            id = 8,
            name = "MK8",
            ipAddress = "38.224.231.4",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
            vlanId = 100
        )
        val subscription = Subscription(
            id = 77,
            firstName = "PPPoE",
            lastName = "Cliente",
            equipmentCondition = EquipmentCondition.LOAN,
            serviceStatus = ServiceStatus.ACTIVE,
        ).apply {
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf77"
            ip = null
            hostDevice = host
        }
        every { repository.findById(77) } returns Optional.of(subscription)
        every { repository.cancelService(77, any()) } returns Unit
        every { repository.save(subscription) } returns subscription
        every { pppoeAccessService.cut(subscription, host) } returns true

        service.cancelService(77) { }

        verify(exactly = 1) { pppoeAccessService.cut(subscription, host) }
        verify(exactly = 0) { mikrotikService.addIpToDebtorsListIfNotExists(any(), any(), any()) }
    }

    @Test
    fun `restoreInternetConnection restores PPPoE profile for a cut subscriber`() {
        val host = NetworkDevice(
            id = 8,
            name = "MK8",
            ipAddress = "38.224.231.4",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
            vlanId = 100
        )
        val subscription = Subscription(
            id = 88,
            firstName = "PPPoE",
            lastName = "Cliente",
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply {
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf88"
            hostDevice = host
            plan = Plan(id = 54, name = "f200", downloadSpeed = 200, uploadSpeed = 200)
        }
        every { paymentRepository.findPendingPaymentsBySubscriptionId(88) } returns 0
        every { repository.findById(88) } returns Optional.of(subscription)
        every { subscriptionLogRepository.save(any()) } answers { firstArg() }
        every { repository.save(subscription) } returns subscription
        every { pppoeAccessService.restore(subscription, host) } returns true

        service.restoreInternetConnection(88, responsibleId = 1)

        verify(exactly = 1) { pppoeAccessService.restore(subscription, host) }
    }

    private fun poolWithOctets(octets: Iterable<Int>): IpPool {
        val occupied = octets.map { octet ->
            Subscription(
                firstName = "Cliente",
                lastName = octet.toString(),
                dni = octet.toString().padStart(8, '0'),
                equipmentCondition = EquipmentCondition.LOAN,
            ).apply { ip = "192.168.30.$octet" }
        }
        return IpPool(
            id = 22,
            ipSegment = "192.168.30.0/24",
            ips = occupied,
            isEligible = true,
        )
    }
}
