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
import org.springframework.web.client.RestClientException
import java.util.Optional

class SubscriptionControllerAcsEndpointsTest {

    private val repository = mockk<SubscriptionRepository>()
    private val gateway = mockk<GatewayOnuActivationClient>()
    private val gatewayCpe = mockk<ObjectProvider<GatewayOnuActivationClient>>()
    private val acsRepository = mockk<com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository>()
    private val subscriptionProvisionService = mockk<com.dscorp.wispadmin.wispadmin.service.SubscriptionProvisionService>()
    private val acsLinkService = mockk<com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsLinkService>()
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
        subscriptionAcsLinkService = acsLinkService,
        subscriptionAcsRepository = acsRepository,
    )

    init {
        every { gatewayCpe.ifAvailable } returns gateway
        every { repository.findById(42) } returns Optional.of(
            Subscription(id = 42, fiberOnuSn = "SN1", equipmentCondition = com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition.values().first()).apply {
                tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE
            }
        )
        every { acsRepository.findById(42) } returns Optional.empty()
    }

    @Test
    fun `GET acs returns 200 with dto`() {
        every { gateway.telemetry("SN1") } returns GatewayCpeTelemetry(sn = "SN1", productClass = "V2804")

        val response = controller.getSubscriptionAcs(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(42, response.body?.subscriptionId)
        assertEquals(Tr069ProvisionStatus.COMPLETE, response.body?.provisionStatus)
        assertEquals("V2804", response.body?.productClass)
        assertEquals(false, response.body?.lab)
        assertEquals(null, response.body?.genieacsDeviceId)
    }

    @Test
    fun `GET acs keeps subscription_acs device id and lab`() {
        every { gateway.telemetry("SN1") } returns GatewayCpeTelemetry(sn = "SN1", productClass = "F6600R")
        every { acsRepository.findById(42) } returns Optional.of(
            com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs(
                subscriptionId = 42,
                genieacsDeviceId = "5872C9-F6600R-ZTEGDC47BFFD",
                lab = true,
                productClass = "F6600R",
            )
        )

        val response = controller.getSubscriptionAcs(42)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("5872C9-F6600R-ZTEGDC47BFFD", response.body?.genieacsDeviceId)
        assertEquals(true, response.body?.lab)
        assertEquals("F6600R", response.body?.productClass)
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
    fun `POST wifi returns gateway ack`() {
        every {
            gateway.setWifi("SN1", match { it.ssid24 == "lab-24" && it.passphrase == "11111111" })
        } returns GatewayCpeCommandResponse(accepted = true, status = "COMPLETE")

        val response = controller.setSubscriptionAcsWifi(
            42,
            com.dscorp.wispadmin.wispadmin.oltclient.GatewayCpeWifiRequest(
                ssid24 = "lab-24",
                ssid5 = "lab-5",
                passphrase = "11111111",
            ),
        )

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

    @Test
    fun `POST retry-tr069 returns sanitized 502 when gateway rejects provisioning`() {
        every {
            subscriptionProvisionService.retryTr069(42)
        } throws RestClientException("400 Bad Request: backend details")

        val response = controller.retryTr069Provisioning(42)

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
        assertEquals(
            "No se pudo completar el reintento TR-069 con el Gateway",
            (response.body as Map<*, *>)["error"],
        )
        assertTrue(response.body.toString().contains("backend details").not())
    }

    @Test
    fun `POST acs link returns 200 LINKED`() {
        every { acsLinkService.link("B46415-V2804AX15T-12345B4641586D819", false) } returns
            com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsLinkResult(
                status = com.dscorp.wispadmin.wispadmin.dto.AcsLinkStatus.LINKED,
                deviceId = "B46415-V2804AX15T-12345B4641586D819",
                subscriptionId = 2070,
            )

        val response = controller.linkSubscriptionAcs(
            com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsLinkRequest(
                deviceId = "B46415-V2804AX15T-12345B4641586D819",
            )
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsLinkResult
        assertEquals(com.dscorp.wispadmin.wispadmin.dto.AcsLinkStatus.LINKED, body.status)
        assertEquals(2070, body.subscriptionId)
    }

    @Test
    fun `POST acs link returns 409 SKIP_AMBIGUOUS`() {
        every { acsLinkService.link("dev-1", false) } returns
            com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsLinkResult(
                status = com.dscorp.wispadmin.wispadmin.dto.AcsLinkStatus.SKIP_AMBIGUOUS,
                deviceId = "dev-1",
            )

        val response = controller.linkSubscriptionAcs(
            com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsLinkRequest(deviceId = "dev-1")
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }
}
