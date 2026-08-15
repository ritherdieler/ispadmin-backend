package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.IInstallationStrategy
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationResult
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.InstallationStrategyFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

class SubscriptionProvisionServiceTest {

    private val repository = mockk<SubscriptionRepository>()
    private val networkDeviceRepository = mockk<NetworkDeviceRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val placeRepository = mockk<PlaceRepository>()
    private val installationStrategyFactory = mockk<InstallationStrategyFactory>()
    private val installationStrategy = mockk<IInstallationStrategy>()
    private val errorLogRepository = mockk<ErrorLogRepository>(relaxed = true)

    private val service = SubscriptionProvisionService(
        repository = repository,
        networkDeviceRepository = networkDeviceRepository,
        planRepository = planRepository,
        placeRepository = placeRepository,
        installationStrategyFactory = installationStrategyFactory,
        errorLogRepository = errorLogRepository
    )

    @Test
    fun `initializeStatuses sets wireless mikrotik pending and olt NA`() {
        val subscription = baseSubscription()
        service.initializeStatuses(subscription, InstallationType.WIRELESS)
        assertEquals(MikrotikProvisionStatus.PENDING, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.NA, subscription.oltProvisionStatus)
    }

    @Test
    fun `initializeStatuses sets fiber both pending`() {
        val subscription = baseSubscription()
        service.initializeStatuses(subscription, InstallationType.FIBER)
        assertEquals(MikrotikProvisionStatus.PENDING, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.PENDING, subscription.oltProvisionStatus)
    }

    @Test
    fun `applyInstallationResult marks complete and clears retry when queue ok`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(queueAdded = true),
            InstallationType.WIRELESS
        )
        assertEquals(MikrotikProvisionStatus.COMPLETE, subscription.mikrotikProvisionStatus)
        assertFalse(subscription.isProvisioningPending())
        assertNull(subscription.provisionNextAttemptAt)
    }

    @Test
    fun `applyInstallationResult schedules backoff when mikrotik pending`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
            provisionAttemptCount = 0
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(queueAdded = false, mikrotikError = "down"),
            InstallationType.WIRELESS
        )
        assertEquals(MikrotikProvisionStatus.PENDING, subscription.mikrotikProvisionStatus)
        assertEquals(1, subscription.provisionAttemptCount)
        assertNotNull(subscription.provisionNextAttemptAt)
        assertTrue(subscription.provisionNextAttemptAt!!.isAfter(LocalDateTime.now().plusMinutes(4)))
        assertEquals("down", subscription.provisionLastError)
    }

    @Test
    fun `applyInstallationResult keeps olt pending when fiber onu fails`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.PENDING
        }
        service.applyInstallationResult(
            subscription,
            InstallationResult(queueAdded = true, onuAuthorized = false, oltError = "olt timeout"),
            InstallationType.FIBER
        )
        assertEquals(MikrotikProvisionStatus.COMPLETE, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.PENDING, subscription.oltProvisionStatus)
        assertTrue(subscription.isProvisioningPending())
        assertTrue(subscription.provisionLastError!!.contains("olt timeout"))
    }

    @Test
    fun `scheduleNextAttempt uses 15 minutes on second attempt`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
            provisionAttemptCount = 1
        }
        val before = LocalDateTime.now()
        service.scheduleNextAttempt(subscription)
        assertEquals(2, subscription.provisionAttemptCount)
        assertNotNull(subscription.provisionNextAttemptAt)
        assertTrue(subscription.provisionNextAttemptAt!!.isAfter(before.plusMinutes(14)))
        assertTrue(subscription.provisionNextAttemptAt!!.isBefore(before.plusMinutes(16)))
    }

    @Test
    fun `scheduleNextAttempt uses 30 minutes from third attempt`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
            provisionAttemptCount = 2
        }
        val before = LocalDateTime.now()
        service.scheduleNextAttempt(subscription)
        assertEquals(3, subscription.provisionAttemptCount)
        assertNotNull(subscription.provisionNextAttemptAt)
        assertTrue(subscription.provisionNextAttemptAt!!.isAfter(before.plusMinutes(29)))
        assertTrue(subscription.provisionNextAttemptAt!!.isBefore(before.plusMinutes(31)))
    }

    @Test
    fun `scheduleNextAttempt marks failed after max attempts`() {
        val subscription = baseSubscription().apply {
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.PENDING
            provisionAttemptCount = SubscriptionProvisionService.MAX_ATTEMPTS
        }
        service.scheduleNextAttempt(subscription)
        assertEquals(MikrotikProvisionStatus.FAILED, subscription.mikrotikProvisionStatus)
        assertEquals(OltProvisionStatus.FAILED, subscription.oltProvisionStatus)
        assertNull(subscription.provisionNextAttemptAt)
    }

    @Test
    fun `reconcile completes pending mikrotik when strategy succeeds`() {
        val subscription = baseSubscription().apply {
            id = 10
            mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING
            oltProvisionStatus = OltProvisionStatus.NA
            hostDevice = NetworkDevice(id = 1)
            plan = Plan(id = 1)
            place = Place(id = 1)
        }
        every { networkDeviceRepository.findById(1) } returns Optional.of(NetworkDevice(id = 1))
        every { planRepository.findById(1) } returns Optional.of(Plan(id = 1))
        every { placeRepository.findById(1) } returns Optional.of(Place(id = 1))
        every { installationStrategyFactory.getStrategy(any()) } returns installationStrategy
        every {
            installationStrategy.processInstallation(any(), any(), any(), any(), any())
        } returns InstallationResult(queueAdded = true)
        every { repository.save(any()) } answers { firstArg() }

        val result = service.reconcile(subscription, sampleRequest())

        assertEquals(MikrotikProvisionStatus.COMPLETE, result.mikrotikProvisionStatus)
        assertFalse(result.isProvisioningPending())
        verify(exactly = 1) { repository.save(subscription) }
    }

    private fun baseSubscription() = Subscription(
        firstName = "Ana",
        lastName = "Lopez",
        dni = "87654321",
        equipmentCondition = EquipmentCondition.LOAN
    ).apply {
        installationType = InstallationType.WIRELESS
    }

    private fun sampleRequest() = SubscriptionRequest(
        firstName = "Ana",
        lastName = "Lopez",
        dni = "87654321",
        address = "Calle 1",
        phone = "999",
        subscriptionDate = System.currentTimeMillis(),
        planId = 1,
        additionalDeviceIds = emptyList(),
        placeId = 1,
        location = GeoLocation(-11.0, -77.0),
        technicianId = 1,
        hostDeviceId = 1,
        installationType = InstallationType.WIRELESS,
        equipmentCondition = EquipmentCondition.LOAN
    )
}
