package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayCpeCommandResponse
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayCpeTelemetry
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import java.util.Optional

class SubscriptionControllerAcsEndpointsTest {

    private val repository = mockk<SubscriptionRepository>()
    private val gateway = mockk<GatewayOnuActivationClient>()
    private val gatewayCpe = mockk<ObjectProvider<GatewayOnuActivationClient>>()
    private val subscriptionProvisionService = mockk<com.dscorp.wispadmin.wispadmin.service.SubscriptionProvisionService>()
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
        subscriptionProvisionService = subscriptionProvisionService,
        gatewayCpe = gatewayCpe,
    )

    init {
        every { gatewayCpe.ifAvailable } returns gateway
        every { repository.findById(42) } returns Optional.of(
            Subscription(id = 42, fiberOnuSn = "SN1", equipmentCondition = com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition.values().first()).apply {
                tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE
            }
        )
    }

    @Test
    fun `GET acs returns 200 with dto`() {
        every { gateway.telemetry("SN1") } returns GatewayCpeTelemetry(sn = "SN1", productClass = "V2804")

        val response = controller.getSubscriptionAcs(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(42, response.body?.subscriptionId)
        assertEquals(Tr069ProvisionStatus.COMPLETE, response.body?.provisionStatus)
        assertEquals("V2804", response.body?.productClass)
    }

    @Test
    fun `GET acs returns 404 when missing`() {
        every { repository.findById(42) } returns Optional.empty()

        val response = controller.getSubscriptionAcs(42)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `POST refresh returns gateway ack`() {
        every { gateway.wifiRefresh("SN1") } returns GatewayCpeCommandResponse(accepted = true, status = "PENDING")

        val response = controller.refreshSubscriptionAcs(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body is GatewayCpeCommandResponse)
        verify { gateway.wifiRefresh("SN1") }
    }

    @Test
    fun `POST reboot returns gateway ack`() {
        every { gateway.reboot("SN1") } returns GatewayCpeCommandResponse(accepted = true, status = "PENDING", message = "ok")

        val response = controller.rebootSubscriptionAcs(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as GatewayCpeCommandResponse
        assertTrue(body.accepted)
    }

    @Test
    fun `POST reboot returns 503 when gateway missing`() {
        every { gatewayCpe.ifAvailable } returns null

        val response = controller.rebootSubscriptionAcs(42)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
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
