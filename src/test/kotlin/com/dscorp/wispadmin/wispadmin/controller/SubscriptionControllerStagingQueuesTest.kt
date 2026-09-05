package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionProvisionService
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.QueueCreationStats
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CompletableFuture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionControllerStagingQueuesTest {

    private fun controller(tag: String): SubscriptionController {
        val environment = GigafiberEnvironmentProperties().apply { this.tag = tag }
        return SubscriptionController(
            repository = mockk(relaxed = true),
            subscriptionService = mockk(relaxed = true),
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
            subscriptionProvisionService = mockk<SubscriptionProvisionService>(relaxed = true),
            gatewayCpe = mockk(relaxed = true),
            environment = environment
        )
    }

    @Test
    fun `generate simple queues is conflict when environment tag is set`() {
        val response = controller("stg").generateSimpleQueue()
        assertEquals(409, response.status)
        assertTrue(response.error.toString().contains("stg"))
    }

    @Test
    fun `generate simple queues still runs in production`() {
        val subscriptionService = mockk<SubscriptionService>(relaxed = true)
        every { subscriptionService.createSubscriptionsSimpleQueue() } returns CompletableFuture.completedFuture(
            QueueCreationStats(0, 0, 0, 0, 0)
        )
        val environment = GigafiberEnvironmentProperties()
        val controller = SubscriptionController(
            repository = mockk(relaxed = true),
            subscriptionService = subscriptionService,
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
            environment = environment
        )

        val response = controller.generateSimpleQueue()

        assertEquals(200, response.status)
        verify(exactly = 1) { subscriptionService.createSubscriptionsSimpleQueue() }
    }
}
