package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsRebootResultDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.repository.CouponRepository
import com.dscorp.wispadmin.wispadmin.repository.NapBoxRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIpConflictNocNotifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsOpsService
import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069AsyncApplicator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus

class SubscriptionControllerAcsEndpointsTest {

    private val subscriptionAcsOpsService = mockk<SubscriptionAcsOpsService>()
    private val subscriptionProvisionService = mockk<com.dscorp.wispadmin.wispadmin.service.SubscriptionProvisionService>()
    private val controller = SubscriptionController(
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
        tr069AsyncApplicator = mockk(relaxed = true),
        subscriptionAcsOpsService = subscriptionAcsOpsService,
        subscriptionProvisionService = subscriptionProvisionService,
    )

    @Test
    fun `GET acs returns 200 with dto`() {
        every { subscriptionAcsOpsService.getAcs(42) } returns SubscriptionAcs(
            subscriptionId = 42,
            genieacsDeviceId = "device-1",
            provisionStatus = Tr069ProvisionStatus.COMPLETE,
        ).toDto()

        val response = controller.getSubscriptionAcs(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(42, response.body?.subscriptionId)
        assertEquals(Tr069ProvisionStatus.COMPLETE, response.body?.provisionStatus)
    }

    @Test
    fun `GET acs returns 404 when missing`() {
        every { subscriptionAcsOpsService.getAcs(42) } throws NoSuchElementException("missing")

        val response = controller.getSubscriptionAcs(42)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `POST refresh returns updated dto`() {
        every { subscriptionAcsOpsService.refresh(42) } returns SubscriptionAcsDto(
            subscriptionId = 42,
            genieacsDeviceId = "device-1",
            softwareVersion = "V2.0",
        )

        val response = controller.refreshSubscriptionAcs(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body is SubscriptionAcsDto)
        assertEquals("V2.0", (response.body as SubscriptionAcsDto).softwareVersion)
        verify { subscriptionAcsOpsService.refresh(42) }
    }

    @Test
    fun `POST reboot returns result dto`() {
        every { subscriptionAcsOpsService.reboot(42) } returns SubscriptionAcsRebootResultDto(
            subscriptionId = 42,
            deviceId = "device-1",
            taskId = "task-1",
            accepted = true,
            message = "Reinicio ONU enviado vía TR-069",
        )

        val response = controller.rebootSubscriptionAcs(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as SubscriptionAcsRebootResultDto
        assertEquals("task-1", body.taskId)
        assertTrue(body.accepted)
    }

    @Test
    fun `POST reboot returns 400 on IllegalStateException`() {
        every { subscriptionAcsOpsService.reboot(42) } throws IllegalStateException("sin deviceId")

        val response = controller.rebootSubscriptionAcs(42)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, String>
        assertEquals("sin deviceId", body["error"])
    }

    @Test
    fun `POST retry-tr069 returns updated subscription dto`() {
        every { subscriptionProvisionService.retryTr069(42) } returns SubscriptionDto(
            id = 42,
            tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE,
            tr069Message = "ONU configurada automáticamente por TR-069.",
        )

        val response = controller.retryTr069Provisioning(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as SubscriptionDto
        assertEquals(Tr069ProvisionStatus.COMPLETE, body.tr069ProvisionStatus)
        verify { subscriptionProvisionService.retryTr069(42) }
    }

    @Test
    fun `POST retry-tr069 returns 400 when OLT not ready`() {
        every {
            subscriptionProvisionService.retryTr069(42)
        } throws IllegalStateException("No se puede reintentar TR-069 hasta que la autorización OLT esté COMPLETE")

        val response = controller.retryTr069Provisioning(42)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `POST retry-tr069 returns 404 when subscription missing`() {
        every { subscriptionProvisionService.retryTr069(42) } throws NoSuchElementException("missing")

        val response = controller.retryTr069Provisioning(42)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
