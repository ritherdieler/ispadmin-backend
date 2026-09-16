package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivateRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivateResponse
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.service.CancelledOnuReuseService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeAccessService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeSecretResult
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider

class FiberInstallationStrategyTest {

    private lateinit var strategy: FiberInstallationStrategy

    private lateinit var environment: GigafiberEnvironmentProperties

    private lateinit var pppoeAccessService: PppoeAccessService

    @BeforeEach
    fun setUp() {
        environment = GigafiberEnvironmentProperties()
        pppoeAccessService = mockk(relaxed = true)
        strategy = buildStrategy(
            mock(CancelledOnuReuseService::class.java),
            mockk(relaxed = true),
            noGateway(),
        )
    }

    private fun buildStrategy(
        onuReuse: CancelledOnuReuseService,
        queueProvisioner: SimpleQueueProvisioner,
        gateway: ObjectProvider<GatewayOnuActivationClient>,
    ) = FiberInstallationStrategy(
        onuReuse,
        queueProvisioner,
        environment,
        gateway,
        pppoeAccessService,
    )

    @Test
    fun `resolveVlan devuelve 100 desde subscription vlan`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 8, vlanId = 100)).apply {
            vlan = "100"
        }

        assertEquals("100", strategy.resolveVlan(subscription))
    }

    @Test
    fun `resolveVlan devuelve 1 desde subscription vlan`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 1, vlanId = 1)).apply {
            vlan = "1"
        }

        assertEquals("1", strategy.resolveVlan(subscription))
    }

    @Test
    fun `resolveVlan payload 100 predomina aunque hostDevice tenga vlanId 1`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 1, vlanId = 1)).apply {
            vlan = "100"
        }

        assertEquals("100", strategy.resolveVlan(subscription))
    }

    @Test
    fun `resolveVlan nulo o vacio falla sin fallback a hostDevice`() {
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val withoutVlan = subscriptionWithHostDevice(host)
        val blankVlan = subscriptionWithHostDevice(host).apply { vlan = "   " }

        assertThrows(IllegalArgumentException::class.java) {
            strategy.resolveVlan(withoutVlan)
        }
        assertThrows(IllegalArgumentException::class.java) {
            strategy.resolveVlan(blankVlan)
        }
    }

    @Test
    fun `resolveVlan falla con vlan invalida aunque hostDevice tenga vlanId`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 8, vlanId = 100)).apply {
            vlan = "50"
        }

        assertThrows(IllegalArgumentException::class.java) {
            strategy.resolveVlan(subscription)
        }
    }

    @Test
    fun `resolveVlan acepta VLAN 100 con pool staging 250 cuando tag es stg`() {
        environment.tag = "stg"
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 8, vlanId = 100)).apply {
            vlan = "100"
            ipPool = IpPool(id = 250, ipSegment = "192.168.250.1/24")
        }

        assertEquals("100", strategy.resolveVlan(subscription))
    }

    @Test
    fun `resolveVlan rechaza VLAN 100 con pool staging 250 sin tag stg`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 8, vlanId = 100)).apply {
            vlan = "100"
            ipPool = IpPool(id = 250, ipSegment = "192.168.250.1/24")
        }

        assertThrows(IllegalArgumentException::class.java) {
            strategy.resolveVlan(subscription)
        }
    }

    @Test
    fun `resolveVlan falla cuando hostDevice esta deshabilitado`() {
        val subscription = subscriptionWithHostDevice(
            cloudCoreRouter(id = 8, vlanId = 100, disabled = true)
        ).apply { vlan = "100" }

        assertThrows(IllegalStateException::class.java) {
            strategy.resolveVlan(subscription)
        }
    }

    @Test
    fun `processInstallation does not throw when MikroTik TLS handshake fails`() {
        val onuReuse = mockk<CancelledOnuReuseService>(relaxed = true)
        val queueProvisioner = mockk<SimpleQueueProvisioner>()
        strategy = buildStrategy(onuReuse, queueProvisioner, noGateway())
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            accessMode = AccessMode.PPPOE_DYNAMIC
            plan = Plan(id = 54, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
            place = Place(id = 4, name = "Huacho")
        }
        every { pppoeAccessService.ensureSecret(any(), any()) } returns
            PppoeSecretResult(error = "rest PUT /rest/queue/simple: Remote host terminated the handshake")

        val result = strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest(),
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!
        )

        assertFalse(result.queueAdded)
        assertEquals(true, result.onuAuthorized)
        assertEquals(
            "rest PUT /rest/queue/simple: Remote host terminated the handshake",
            result.mikrotikError
        )
        verify(exactly = 0) { queueProvisioner.ensureQueue(any(), any(), any()) }
    }

    @Test
    fun `processInstallation static ip keeps mode and creates simple queue`() {
        val onuReuse = mockk<CancelledOnuReuseService>(relaxed = true)
        val queueProvisioner = mockk<SimpleQueueProvisioner>()
        every { queueProvisioner.ensureQueue(any(), any(), any()) } returns QueueEnsureResult(added = true)
        strategy = buildStrategy(onuReuse, queueProvisioner, noGateway())
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            ip = "192.168.250.20"
            accessMode = AccessMode.STATIC_IP
            plan = Plan(id = 54, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
            place = Place(id = 4, name = "Huacho")
        }

        val result = strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest().apply {
                accessMode = AccessMode.STATIC_IP
                clientIpAddress = "192.168.250.20"
                onu = OnuDto(
                    sn = "VSOL0031C0B6",
                    board = "1",
                    olt_id = "gigafiber-ma5608t",
                    onu = "",
                    onu_type_id = "",
                    onu_type_name = "V2804AX15T",
                    pon_type = "gpon",
                    port = "6",
                )
            },
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!
        )

        assertTrue(result.queueAdded)
        assertEquals(AccessMode.STATIC_IP, subscription.accessMode)
        assertNull(subscription.pppoeUsername)
        verify { queueProvisioner.ensureQueue(subscription, host, subscription.plan!!) }
        verify(exactly = 0) { pppoeAccessService.ensureSecret(any(), any()) }
    }

    @Test
    fun `processInstallation con PPPOE_DYNAMIC crea el secret y no crea simple queue`() {
        val onuReuse = mockk<CancelledOnuReuseService>(relaxed = true)
        val queueProvisioner = mockk<SimpleQueueProvisioner>()
        strategy = buildStrategy(onuReuse, queueProvisioner, noGateway())
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf4321"
            plan = Plan(id = 54, name = "f200", downloadSpeed = 200, uploadSpeed = 200)
            place = Place(id = 4, name = "Huacho")
        }
        every { pppoeAccessService.ensureSecret(subscription, host) } returns
            PppoeSecretResult(created = true, profile = "GF-200-200")

        val result = strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest(),
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!
        )

        assertTrue(result.queueAdded)
        assertNull(result.mikrotikError)
        verify(exactly = 0) { queueProvisioner.ensureQueue(any(), any(), any()) }
    }

    @Test
    fun `processInstallation con PPPOE_DYNAMIC propaga el error del secret sin lanzar`() {
        val onuReuse = mockk<CancelledOnuReuseService>(relaxed = true)
        val queueProvisioner = mockk<SimpleQueueProvisioner>()
        strategy = buildStrategy(onuReuse, queueProvisioner, noGateway())
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf4321"
            plan = Plan(id = 54, name = "f200", downloadSpeed = 200, uploadSpeed = 200)
            place = Place(id = 4, name = "Huacho")
        }
        every { pppoeAccessService.ensureSecret(subscription, host) } returns
            PppoeSecretResult(error = "Remote host terminated the handshake")

        val result = strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest(),
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!
        )

        assertFalse(result.queueAdded)
        assertEquals("Remote host terminated the handshake", result.mikrotikError)
    }

    @Test
    fun `processInstallation uses gateway activate and returns partial CPE`() {
        val gateway = mockk<GatewayOnuActivationClient>()
        val provider = mockk<ObjectProvider<GatewayOnuActivationClient>>()
        every { provider.ifAvailable } returns gateway
        every { gateway.activate(any()) } returns GatewayOnuActivateResponse(
            uniqueExternalId = "gigafiber-ma5608t_1_0_5",
            sn = "ALCL12345678",
            oltStatus = "COMPLETE",
            cpeStatus = "PENDING",
        )
        val onuReuse = mockk<CancelledOnuReuseService>(relaxed = true)
        val queueProvisioner = mockk<SimpleQueueProvisioner>()
        every { pppoeAccessService.ensureSecret(any(), any()) } returns
            PppoeSecretResult(created = true, profile = "GF-50-50")
        strategy = buildStrategy(onuReuse, queueProvisioner, provider)
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            accessMode = AccessMode.PPPOE_DYNAMIC
            plan = Plan(id = 54, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
            place = Place(id = 4, name = "Huacho")
            fiberOnuSn = "ALCL12345678"
        }

        val result = strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest(),
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!
        )

        assertTrue(result.onuAuthorized)
        assertEquals("PENDING", result.cpeStatus)
        assertEquals("gigafiber-ma5608t_1_0_5", result.uniqueExternalId)
        verify { gateway.activate(match<GatewayOnuActivateRequest> { it.sn == "ALCL12345678" && it.vlan == "100" && it.pppoeUsername == "gf100" }) }
        verify(exactly = 0) { onuReuse.authorizeWithCancelledReuse(any()) }
    }

    @Test
    fun `processInstallation static ip envia IP al Gateway sin PPPoE`() {
        val gateway = mockk<GatewayOnuActivationClient>()
        val provider = mockk<ObjectProvider<GatewayOnuActivationClient>>()
        every { provider.ifAvailable } returns gateway
        every { gateway.activate(any()) } returns GatewayOnuActivateResponse(
            uniqueExternalId = "gigafiber-ma5608t_1_6_116",
            sn = "VSOL0031C0B6",
            oltStatus = "COMPLETE",
            cpeStatus = "PENDING",
        )
        val queueProvisioner = mockk<SimpleQueueProvisioner>()
        every { queueProvisioner.ensureQueue(any(), any(), any()) } returns QueueEnsureResult(added = true)
        strategy = buildStrategy(mockk(relaxed = true), queueProvisioner, provider)
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            ip = "192.168.250.20"
            accessMode = AccessMode.STATIC_IP
            plan = Plan(id = 1, name = "basico", downloadSpeed = 200, uploadSpeed = 200)
            place = Place(id = 1, name = "9 de octubre")
            fiberOnuSn = "VSOL0031C0B6"
        }

        strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest().apply {
                accessMode = AccessMode.STATIC_IP
                onu = OnuDto(
                    sn = "VSOL0031C0B6",
                    board = "1",
                    olt_id = "gigafiber-ma5608t",
                    onu = "",
                    onu_type_id = "",
                    onu_type_name = "V2804AX15T",
                    pon_type = "gpon",
                    port = "6",
                )
            },
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!,
        )

        verify {
            gateway.activate(
                match<GatewayOnuActivateRequest> {
                    it.sn == "VSOL0031C0B6" &&
                        it.ip == "192.168.250.20" &&
                        it.pppoeUsername == null &&
                        it.pppoePassword == null
                }
            )
        }
        verify(exactly = 0) { pppoeAccessService.ensureSecret(any(), any()) }
    }

    @Test
    fun `processInstallation PPPoE envia usuario y password al Gateway sin IP`() {
        val gateway = mockk<GatewayOnuActivationClient>()
        val provider = mockk<ObjectProvider<GatewayOnuActivationClient>>()
        every { provider.ifAvailable } returns gateway
        every { gateway.activate(any()) } returns GatewayOnuActivateResponse(
            uniqueExternalId = "gigafiber-ma5608t_1_6_116",
            sn = "VSOL0031C0B6",
            oltStatus = "COMPLETE",
            cpeStatus = "PENDING",
        )
        every { pppoeAccessService.decryptedPassword(any()) } returns "secreto123"
        every { pppoeAccessService.ensureSecret(any(), any()) } returns
            PppoeSecretResult(created = true, profile = "GF-200-200")
        strategy = buildStrategy(mockk(relaxed = true), mockk(relaxed = true), provider)
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            ip = null
            ipPool = null
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf2397"
            pppoePasswordEnc = "enc:v1:cifrado"
            plan = Plan(id = 1, name = "basico", downloadSpeed = 200, uploadSpeed = 200)
            place = Place(id = 1, name = "9 de octubre")
            fiberOnuSn = "VSOL0031C0B6"
        }

        strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest().apply {
                onu = OnuDto(
                    sn = "VSOL0031C0B6",
                    board = "1",
                    olt_id = "gigafiber-ma5608t",
                    onu = "",
                    onu_type_id = "",
                    onu_type_name = "V2804AX15T",
                    pon_type = "gpon",
                    port = "6",
                )
            },
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!,
        )

        verify {
            gateway.activate(
                match<GatewayOnuActivateRequest> {
                    it.sn == "VSOL0031C0B6" &&
                        it.ip == null &&
                        it.pppoeUsername == "gf2397" &&
                        it.pppoePassword == "secreto123"
                }
            )
        }
    }

    @Test
    fun `processInstallation falls back to SmartOLT when gateway client unavailable`() {
        val onuReuse = mockk<CancelledOnuReuseService>(relaxed = true)
        val queueProvisioner = mockk<SimpleQueueProvisioner>()
        every { pppoeAccessService.ensureSecret(any(), any()) } returns
            PppoeSecretResult(created = true, profile = "GF-50-50")
        strategy = buildStrategy(onuReuse, queueProvisioner, noGateway())
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            accessMode = AccessMode.PPPOE_DYNAMIC
            plan = Plan(id = 54, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
            place = Place(id = 4, name = "Huacho")
        }

        val result = strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest(),
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!
        )

        assertTrue(result.onuAuthorized)
        verify(exactly = 1) { onuReuse.authorizeWithCancelledReuse(any()) }
    }

    private fun noGateway(): ObjectProvider<GatewayOnuActivationClient> {
        val provider = mockk<ObjectProvider<GatewayOnuActivationClient>>()
        every { provider.ifAvailable } returns null
        return provider
    }

    private fun cloudCoreRouter(id: Int, vlanId: Int?, disabled: Boolean = false): NetworkDevice =
        NetworkDevice(
            id = id,
            name = "MK$id",
            ipAddress = if (id == 8) "38.224.231.4" else "38.224.231.2",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
            vlanId = vlanId,
            disabled = disabled
        )

    private fun subscriptionWithHostDevice(hostDevice: NetworkDevice): Subscription =
        Subscription(
            id = 100,
            firstName = "Juan",
            lastName = "Perez",
            hostDevice = hostDevice,
            ip = "192.168.25.10",
            equipmentCondition = EquipmentCondition.LOAN
        )

    private fun fiberRequest() = SubscriptionRequest(
        firstName = "Juan",
        lastName = "Perez",
        dni = "12345678",
        address = "Calle 1",
        phone = "999888777",
        subscriptionDate = System.currentTimeMillis(),
        planId = 54,
        additionalDeviceIds = emptyList(),
        placeId = 4,
        location = GeoLocation(-11.0, -77.0),
        technicianId = 1,
        napBoxId = 63,
        hostDeviceId = 8,
        installationType = InstallationType.FIBER,
        onu = OnuDto(sn = "ALCL12345678", board = "1", olt_id = "1", onu = "1", onu_type_id = "1", onu_type_name = "HG8240H", pon_type = "gpon", port = "1"),
        clientRequestId = "offline-req-1",
        vlan = "100",
    )
}
