package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.AccessMigrationStage
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.AccessMigrationEligibleDto
import com.dscorp.wispadmin.wispadmin.dto.AccessMigrationProgressDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionProvisionService
import com.dscorp.wispadmin.wispadmin.service.subscription.AccessMigrationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import java.util.Optional

class SubscriptionControllerAccessMigrationTest {

    private val repository = mockk<SubscriptionRepository>()
    private val accessMigrationService = mockk<AccessMigrationService>()
    private val controller = SubscriptionController(
        repository = repository,
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
        gatewayCpe = mockk<ObjectProvider<com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient>>(relaxed = true),
        subscriptionAcsLinkService = mockk(relaxed = true),
        accessMigrationService = accessMigrationService,
    )

    @Test
    fun `POST starts migration and returns progress`() {
        every { accessMigrationService.start(1001) } returns AccessMigrationProgressDto(
            subscriptionId = 1001,
            stage = AccessMigrationStage.ELIGIBLE,
            attempt = 1,
        )

        val response = controller.startAccessMigration(1001)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as AccessMigrationProgressDto
        assertEquals(AccessMigrationStage.ELIGIBLE, body.stage)
        verify { accessMigrationService.start(1001) }
    }

    @Test
    fun `POST returns 400 when the subscription is not eligible`() {
        every { accessMigrationService.start(1001) } throws IllegalStateException("Solo FIBER es migrable")

        val response = controller.startAccessMigration(1001)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `GET progress returns 404 when there is no migration row`() {
        every { accessMigrationService.progress(1001) } returns null

        val response = controller.getAccessMigration(1001)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `GET eligible returns only the service list`() {
        every { accessMigrationService.listEligible() } returns listOf(
            AccessMigrationEligibleDto(1001, "Ana Fiber", "192.168.1.50", "VSOL0031C0B6", eligible = true)
        )

        val response = controller.listEligibleAccessMigrations()

        assertEquals(1, response.body?.items?.size)
        assertEquals(1001, response.body?.items?.first()?.subscriptionId)
    }

    @Test
    fun `GET subscription exposes accessMode username and migration stage`() {
        val subscription = Subscription(
            id = 1001,
            firstName = "Ana",
            lastName = "Fiber",
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply {
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf1001"
        }
        every { repository.findById(1001) } returns Optional.of(subscription)
        every { accessMigrationService.progress(1001) } returns AccessMigrationProgressDto(
            subscriptionId = 1001,
            stage = AccessMigrationStage.QUARANTINE,
            attempt = 1,
            pppoeUsername = "gf1001",
        )

        val response = controller.getSubscription(1001)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(AccessMode.PPPOE_DYNAMIC, response.body?.accessMode)
        assertEquals("gf1001", response.body?.pppoeUsername)
        assertEquals(AccessMigrationStage.QUARANTINE, response.body?.accessMigrationStage)
        assertEquals(AccessMigrationStage.QUARANTINE, response.body?.accessMigration?.stage)
        assertEquals("gf1001", response.body?.accessMigration?.pppoeUsername)
        assertEquals(true, response.body?.accessMigration?.done)
        assertNull(response.body?.pppoeUsername?.takeIf { it.isBlank() })
    }
}
