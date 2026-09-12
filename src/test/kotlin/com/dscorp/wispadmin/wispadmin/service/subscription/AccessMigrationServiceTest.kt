package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreCpeAccessLayout
import com.dscorp.wispadmin.wispadmin.acsclient.CoreCpeProvisionRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreCpeProvisionResponse
import com.dscorp.wispadmin.wispadmin.config.PppoeProperties
import com.dscorp.wispadmin.wispadmin.data.model.AccessMigrationStage
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAccessMigration
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayServicePortsDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAccessMigrationRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeManagerService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeSecretResult
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeSession
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import java.util.Optional

class AccessMigrationServiceTest {

    private val subscriptions = mockk<SubscriptionRepository>()
    private val migrations = mockk<SubscriptionAccessMigrationRepository>(relaxed = true)
    private val acsRecords = mockk<SubscriptionAcsRepository>(relaxed = true)
    private val acs = mockk<AcsCpeCoreClient>()
    private val gateway = mockk<GatewayOnuActivationClient>(relaxed = true)
    private val pppoeManager = mockk<PppoeManagerService>()
    private val mikrotik = mockk<IMikroTikService>(relaxed = true)
    private val cipher = mockk<CrmSecretCipher>(relaxed = true)
    private lateinit var service: AccessMigrationService
    private val rows = mutableListOf<SubscriptionAccessMigration>()

    @BeforeEach
    fun setUp() {
        rows.clear()
        val acsClients = mockk<ObjectProvider<AcsCpeCoreClient>>()
        val gatewayClients = mockk<ObjectProvider<GatewayOnuActivationClient>>()
        every { acsClients.ifAvailable } returns acs
        every { gatewayClients.ifAvailable } returns gateway
        every { migrations.save(any()) } answers {
            val row = firstArg<SubscriptionAccessMigration>()
            if (row.id == null) row.id = 1L
            rows.removeIf { it.id == row.id }
            rows.add(row)
            row
        }
        every { migrations.findTopBySubscriptionIdOrderByAttemptDesc(any()) } answers {
            val id = firstArg<Int>()
            rows.lastOrNull { it.subscriptionId == id }
        }
        every { subscriptions.save(any()) } answers { firstArg() }
        every { cipher.encrypt(any()) } returns "enc:secret"
        every { cipher.looksEncrypted(any()) } returns true
        every { cipher.decrypt(any()) } returns "secret"
        service = AccessMigrationService(
            subscriptions,
            migrations,
            acsRecords,
            acsClients,
            gatewayClients,
            pppoeManager,
            mikrotik,
            cipher,
            PppoeProperties(),
        )
        service.asyncRunner = { it.run() }
        service.sessionRunner = { _, block -> block(mockk(relaxed = true)) }
    }

    @Test
    fun `start aborts when WANIP and WANPPP do not share a slot`() {
        val subscription = fiberStatic()
        every { subscriptions.findById(1001) } returns Optional.of(subscription)
        every { acs.accessLayout("VSOL0031C0B6") } returns sharedLayout().copy(
            wanIpPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            wanPppPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1",
        )

        val error = assertThrows(IllegalStateException::class.java) { service.start(1001) }

        assertTrue(error.message!!.contains("dos WAN"))
        verify(exactly = 0) { migrations.save(any()) }
    }

    @Test
    fun `start continues when ACS vparams layout has empty WAN paths`() {
        val subscription = fiberStatic()
        stubHappyPath(subscription)
        every { acs.accessLayout("VSOL0031C0B6") } returns sharedLayout().copy(
            wanIpPath = null,
            wanPppPath = null,
            wanIpSharesPppSlot = false,
        )

        val progress = service.start(1001)

        assertEquals(AccessMigrationStage.QUARANTINE, progress.stage)
        verify(exactly = 1) { acs.provision(any()) }
    }

    @Test
    fun `start rejects a subscription that is not FIBER`() {
        val subscription = fiberStatic().apply { installationType = InstallationType.WIRELESS }
        every { subscriptions.findById(1001) } returns Optional.of(subscription)
        every { acs.accessLayout("VSOL0031C0B6") } returns sharedLayout()

        val error = assertThrows(IllegalStateException::class.java) { service.start(1001) }

        assertEquals("Solo FIBER es migrable", error.message)
    }

    @Test
    fun `happy path reaches quarantine as PPPOE_DYNAMIC without touching WiFi`() {
        val subscription = fiberStatic()
        val captured = mutableListOf<CoreCpeProvisionRequest>()
        stubHappyPath(subscription)
        every { acs.provision(any()) } answers {
            val request = firstArg<CoreCpeProvisionRequest>()
            captured.add(request)
            CoreCpeProvisionResponse(sn = request.sn, status = "COMPLETE")
        }

        val progress = service.start(1001)

        assertEquals(AccessMigrationStage.QUARANTINE, progress.stage)
        assertEquals(true, progress.done)
        assertEquals("En cuarentena", progress.message)
        assertEquals(AccessMode.PPPOE_DYNAMIC, subscription.accessMode)
        assertEquals("gf1001", subscription.pppoeUsername)
        assertEquals("192.168.1.50", subscription.ip)
        assertEquals("10.64.0.25", subscription.pppoeLastIp)
        assertEquals(1, captured.size)
        assertNull(captured.first().wifiSsid24)
        assertNull(captured.first().wifiPassword24)
        assertEquals(100, captured.first().wanVlanId)
        assertEquals("gf1001", captured.first().pppoeUsername)
        verify { gateway.servicePorts("VSOL0031C0B6") }
        verify { pppoeManager.ensureSecret(any(), subscription, "secret") }
        verify { mikrotik.findAndRemoveQueueByIp(any(), "192.168.1.50") }
    }

    @Test
    fun `verify failure with recent Inform reverts the CPE`() {
        val subscription = fiberStatic()
        val captured = mutableListOf<CoreCpeProvisionRequest>()
        stubHappyPath(subscription)
        every { acs.provision(any()) } answers {
            val request = firstArg<CoreCpeProvisionRequest>()
            captured.add(request)
            CoreCpeProvisionResponse(sn = request.sn, status = "COMPLETE")
        }
        every { pppoeManager.sessionOf(any(), "gf1001") } returns null

        val progress = service.start(1001)

        assertEquals(AccessMigrationStage.FAILED_REVERTED, progress.stage)
        assertEquals(2, captured.size)
        assertEquals("192.168.1.50", captured.last().ip)
        assertNull(captured.last().pppoeUsername)
        assertEquals(AccessMode.STATIC_IP, subscription.accessMode)
    }

    @Test
    fun `finishQuarantine removes VLAN 1 and releases the static IP`() {
        val subscription = fiberStatic().apply { accessMode = AccessMode.PPPOE_DYNAMIC }
        every { subscriptions.findById(1001) } returns Optional.of(subscription)
        val row = SubscriptionAccessMigration(
            id = 9,
            subscriptionId = 1001,
            stage = AccessMigrationStage.QUARANTINE,
            previousIp = "192.168.1.50",
            previousVlan = "1",
        )

        service.finishQuarantine(row)

        verify { gateway.removeServicePort("VSOL0031C0B6", 1) }
        assertNull(subscription.ip)
        assertEquals(AccessMigrationStage.DONE, row.stage)
    }

    private fun stubHappyPath(subscription: Subscription) {
        every { subscriptions.findById(1001) } returns Optional.of(subscription)
        every { acs.accessLayout("VSOL0031C0B6") } returns sharedLayout()
        every { gateway.servicePorts("VSOL0031C0B6") } returns GatewayServicePortsDto(
            sn = "VSOL0031C0B6",
            vlans = setOf(1, 100),
        )
        every { pppoeManager.ensureSecret(any(), subscription, "secret") } returns
            PppoeSecretResult(created = true, profile = "GF-200-200")
        every { acs.provision(any()) } returns
            CoreCpeProvisionResponse(sn = "VSOL0031C0B6", status = "COMPLETE")
        every { pppoeManager.sessionOf(any(), "gf1001") } returns PppoeSession(
            username = "gf1001",
            address = "10.64.0.25/32",
            uptime = "1s",
            callerId = null,
            service = "pppoe",
        )
    }

    private fun fiberStatic() = Subscription(
        id = 1001,
        firstName = "Ana",
        lastName = "Fiber",
        equipmentCondition = EquipmentCondition.LOAN,
        serviceStatus = ServiceStatus.ACTIVE,
        installationType = InstallationType.FIBER,
        plan = Plan(id = 54, name = "FIBER 200", price = 80.0, downloadSpeed = 200, uploadSpeed = 200, type = InstallationType.FIBER),
        hostDevice = NetworkDevice(id = 8, name = "MK2"),
        vlan = "1",
    ).apply {
        ip = "192.168.1.50"
        accessMode = AccessMode.STATIC_IP
        fiberOnuSn = "VSOL0031C0B6"
    }

    private fun sharedLayout() = CoreCpeAccessLayout(
        sn = "VSOL0031C0B6",
        productClass = "V2804AX15T",
        connectionRequestUrl = "http://192.168.253.40:7547/",
        lastInformAt = Instant.now().toString(),
        wanIpPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
        wanPppPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2",
        hasPppPath = true,
        wanIpSharesPppSlot = true,
    )
}
