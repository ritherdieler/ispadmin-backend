package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionProvisionService
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningJournal
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningOperation
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.Optional

class SubscriptionControllerRetryTr069V2Test {

    private val subscriptions = mockk<SubscriptionRepository>()
    private val provisionService = mockk<SubscriptionProvisionService>()
    private val journal = mockk<ProvisioningJournal>()
    private val controller = SubscriptionController(
        repository = subscriptions,
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
        subscriptionProvisionService = provisionService,
        gatewayCpe = mockk(relaxed = true),
        subscriptionAcsLinkService = mockk(relaxed = true),
        provisioningJournal = journal,
    )

    @Test
    fun `retry tr069 on a v2 subscription requeues the journal operation instead of the legacy gateway path`() {
        val subscription = Subscription(
            id = 42,
            equipmentCondition = EquipmentCondition.LOAN,
            installationType = InstallationType.FIBER,
            oltProvisionStatus = OltProvisionStatus.COMPLETE,
            tr069ProvisionStatus = Tr069ProvisionStatus.FAILED,
        )
        val failed = ProvisioningOperation(
            id = "op-42", environment = "prod", subscriptionId = 42, serial = "ONU123456789",
            flowVersion = 3, state = ProvisioningState.FAILED, revision = 9,
        )
        every { subscriptions.findById(42) } returns Optional.of(subscription)
        every { subscriptions.save(any()) } answers { firstArg() }
        every { journal.latest("prod", 42) } returns failed
        every { journal.requestRetry("prod", "op-42", 9) } returns failed.copy(state = ProvisioningState.PENDING, revision = 10)

        val response = controller.retryTr069Provisioning(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as SubscriptionDto
        assertEquals(Tr069ProvisionStatus.PENDING, body.tr069ProvisionStatus)
        assertEquals(true, body.provisioningPending)
        verify(exactly = 1) { journal.requestRetry("prod", "op-42", 9) }
        verify(exactly = 0) { provisionService.retryTr069(any()) }
    }

    @Test
    fun `retry tr069 on a non retryable v2 failure is rejected`() {
        val subscription = Subscription(id = 42, equipmentCondition = EquipmentCondition.LOAN)
        val failed = ProvisioningOperation(
            id = "op-42", environment = "prod", subscriptionId = 42, serial = "ONU123456789",
            flowVersion = 3, state = ProvisioningState.FAILED, revision = 9,
        )
        every { subscriptions.findById(42) } returns Optional.of(subscription)
        every { journal.latest("prod", 42) } returns failed
        every { journal.requestRetry("prod", "op-42", 9) } throws IllegalStateException("CORRECTION_REQUIRED")

        val response = controller.retryTr069Provisioning(42)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(exactly = 0) { provisionService.retryTr069(any()) }
    }
}
