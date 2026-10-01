package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.SubscriptionProvisionService
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.CheckpointState
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningJournal
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningOperation
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningStage
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningState
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.StageCheckpoint
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class SubscriptionControllerRegistrationProgressOrderTest {

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
        integrityViolationClassifier = com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier(),
        ipConflictNocNotifier = mockk(relaxed = true),
        subscriptionProvisionService = provisionService,
        gatewayCpe = mockk(relaxed = true),
        subscriptionAcsLinkService = mockk(relaxed = true),
        provisioningJournal = journal,
    )

    @Test
    fun `v3 progress places preauthorization checkpoints first`() {
        val subscription = Subscription(
            id = 42,
            equipmentCondition = EquipmentCondition.LOAN,
            oltProvisionStatus = OltProvisionStatus.COMPLETE,
            tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
        )
        val operation = ProvisioningOperation(
            id = "registration-v3",
            environment = "prod",
            subscriptionId = 42,
            serial = "ONU123456789",
            flowVersion = 3,
            state = ProvisioningState.RUNNING,
            checkpoints = ProvisioningStage.values().map { stage ->
                StageCheckpoint(
                    stage = stage,
                    state = if (stage in PREAUTHORIZATION_STAGES) CheckpointState.SUCCEEDED else CheckpointState.PENDING,
                )
            },
        )
        every { subscriptions.findById(42) } returns Optional.of(subscription)
        every { provisionService.refreshTr069FromGateway(subscription) } returns subscription
        every { journal.latest("prod", 42) } returns operation

        val response = controller.getRegistrationProgress(42)

        assertEquals(
            listOf("OLT", "ACS_CONTACT", "VALIDATE", "MIKROTIK", "INTERNET", "WIFI", "WAN_CLEANUP", "VERIFY"),
            response.body?.provisioningCheckpoints?.map { it.stage },
        )
    }

    @Test
    fun `v2 progress exposes the supported executor stages`() {
        val subscription = Subscription(
            id = 42,
            equipmentCondition = EquipmentCondition.LOAN,
            oltProvisionStatus = OltProvisionStatus.COMPLETE,
            tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
        )
        val operation = ProvisioningOperation(
            id = "registration-v2",
            environment = "prod",
            subscriptionId = 42,
            serial = "ONU123456789",
            flowVersion = 2,
            state = ProvisioningState.RUNNING,
        )
        every { subscriptions.findById(42) } returns Optional.of(subscription)
        every { provisionService.refreshTr069FromGateway(subscription) } returns subscription
        every { journal.latest("prod", 42) } returns operation

        val response = controller.getRegistrationProgress(42)

        assertEquals(
            ProvisioningStage.values().map { it.name },
            response.body?.provisioningCheckpoints?.map { it.stage },
        )
    }

    @Test
    fun `failed v2 operation reports FAILED outcome without calling the gateway`() {
        val subscription = Subscription(
            id = 42,
            equipmentCondition = EquipmentCondition.LOAN,
            oltProvisionStatus = OltProvisionStatus.COMPLETE,
            mikrotikProvisionStatus = com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus.COMPLETE,
            tr069ProvisionStatus = Tr069ProvisionStatus.FAILED,
        )
        val failure = com.dscorp.wispadmin.wispadmin.service.provisioningv2.ProvisioningFailure(
            "WIFI_NOT_CONFIRMED", "El ACS no confirmó los SSID.", true,
        )
        val operation = ProvisioningOperation(
            id = "registration-failed",
            environment = "prod",
            subscriptionId = 42,
            serial = "ONU123456789",
            flowVersion = 3,
            state = ProvisioningState.FAILED,
            checkpoints = ProvisioningStage.values().map { stage ->
                when (stage) {
                    ProvisioningStage.WIFI -> StageCheckpoint(stage, CheckpointState.FAILED, attempts = 1, failure = failure)
                    ProvisioningStage.WAN_CLEANUP, ProvisioningStage.VERIFY -> StageCheckpoint(stage)
                    else -> StageCheckpoint(stage, CheckpointState.SUCCEEDED)
                }
            },
        )
        every { subscriptions.findById(42) } returns Optional.of(subscription)
        every { journal.latest("prod", 42) } returns operation

        val body = controller.getRegistrationProgress(42).body!!

        assertEquals(com.dscorp.wispadmin.wispadmin.dto.RegistrationStep.FAILED, body.step)
        assertEquals(true, body.done)
        assertEquals("FAILED", body.outcome)
        assertEquals("El ACS no confirmó los SSID.", body.message)
        io.mockk.verify(exactly = 0) { provisionService.refreshTr069FromGateway(any()) }
    }

    @Test
    fun `running v2 operation is not done even if subscription statuses look settled`() {
        val subscription = Subscription(
            id = 42,
            equipmentCondition = EquipmentCondition.LOAN,
            oltProvisionStatus = OltProvisionStatus.COMPLETE,
            mikrotikProvisionStatus = com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus.COMPLETE,
            tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE,
        )
        val operation = ProvisioningOperation(
            id = "registration-running", environment = "prod", subscriptionId = 42,
            serial = "ONU123456789", flowVersion = 3, state = ProvisioningState.WAITING,
        )
        every { subscriptions.findById(42) } returns Optional.of(subscription)
        every { journal.latest("prod", 42) } returns operation

        val body = controller.getRegistrationProgress(42).body!!

        assertEquals(false, body.done)
        assertEquals("WAITING", body.outcome)
    }

    @Test
    fun `legacy subscription without journal keeps gateway refresh and has no outcome`() {
        val subscription = Subscription(
            id = 43,
            equipmentCondition = EquipmentCondition.LOAN,
            oltProvisionStatus = OltProvisionStatus.COMPLETE,
            tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
        )
        every { subscriptions.findById(43) } returns Optional.of(subscription)
        every { provisionService.refreshTr069FromGateway(subscription) } returns subscription
        every { journal.latest("prod", 43) } returns null

        val body = controller.getRegistrationProgress(43).body!!

        assertEquals(null, body.outcome)
        io.mockk.verify(exactly = 1) { provisionService.refreshTr069FromGateway(subscription) }
    }

    private companion object {
        val PREAUTHORIZATION_STAGES = setOf(
            ProvisioningStage.OLT,
            ProvisioningStage.ACS_CONTACT,
        )
    }
}
