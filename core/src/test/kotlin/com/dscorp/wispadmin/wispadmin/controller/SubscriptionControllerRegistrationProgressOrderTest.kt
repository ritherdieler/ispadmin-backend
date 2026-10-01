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

    private companion object {
        val PREAUTHORIZATION_STAGES = setOf(
            ProvisioningStage.OLT,
            ProvisioningStage.ACS_CONTACT,
        )
    }
}
