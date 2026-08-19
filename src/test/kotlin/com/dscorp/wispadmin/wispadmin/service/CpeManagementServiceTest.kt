package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.wispadmin.cpe.AcsState
import com.dscorp.wispadmin.wispadmin.cpe.CpeConfigurationOutcome
import com.dscorp.wispadmin.wispadmin.cpe.CpeDeviceAdapter
import com.dscorp.wispadmin.wispadmin.cpe.CpeDeviceDescriptor
import com.dscorp.wispadmin.wispadmin.cpe.CpeCapabilityResolver
import com.dscorp.wispadmin.wispadmin.cpe.CpeWarnings
import com.dscorp.wispadmin.wispadmin.cpe.DefaultCpeCapabilityStrategy
import com.dscorp.wispadmin.wispadmin.cpe.GponState
import com.dscorp.wispadmin.wispadmin.cpe.VsolCpeCapabilityStrategy
import com.dscorp.wispadmin.wispadmin.cpe.WanManagement
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.CpeNetworkConfigRequest
import com.dscorp.wispadmin.wispadmin.dto.CpeWifiConfigRequest
import com.dscorp.wispadmin.wispadmin.dto.UpdateCpeConfigRequest
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

class CpeManagementServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val oltMgrOnuRepository = mockk<OltMgrOnuRepository>(relaxed = true)
    private val cpeDeviceAdapter = mockk<CpeDeviceAdapter>()
    private val oltService = mockk<OltService>()
    private val service = CpeManagementService(
        subscriptionRepository = subscriptionRepository,
        oltMgrOnuRepository = oltMgrOnuRepository,
        cpeDeviceAdapter = cpeDeviceAdapter,
        oltService = oltService,
        capabilityResolver = CpeCapabilityResolver(
            listOf(VsolCpeCapabilityStrategy(), DefaultCpeCapabilityStrategy())
        ),
        cpeConfigValidator = CpeConfigValidator(),
    )

    private val vsolDescriptor = CpeDeviceDescriptor(
        serialNumber = VSOL_SN,
        deviceId = "B46415-V2804AX15T-12345B46415F5E946",
        vendor = "B46415",
        model = "V2804AX15T",
        lastInform = "2026-08-19T18:00:00.000Z",
        reachable = true,
    )

    private val huaweiDescriptor = CpeDeviceDescriptor(
        serialNumber = HUAWEI_SN,
        deviceId = "00259E-EG8145V5-HWTC15F5CD86",
        vendor = "00259E",
        model = "EG8145V5",
        lastInform = "2026-08-19T18:00:00.000Z",
        reachable = true,
    )

    @Test
    fun `V-SOL routes WAN through the OLT and wifi through TR-069`() {
        val subscription = givenSubscription(650, VSOL_SN)
        val oltOnu = givenOltOnu(VSOL_SN)
        every { cpeDeviceAdapter.describe(VSOL_SN) } returns vsolDescriptor
        every {
            oltService.updateOnuWanConfig(
                VSOL_SN, 100, "192.168.30.50", "255.255.255.0", "192.168.30.1", "8.8.8.8", "8.8.4.4"
            )
        } returns OnuWanUpdateResult(applied = true, uniqueExternalId = "1_1_0_5")
        every {
            cpeDeviceAdapter.applyConfiguration(VSOL_SN, null, "GigaFiber-Casa", "clavewifi1")
        } returns CpeConfigurationOutcome(appliedNetwork = false, appliedWifi = true)

        val response = service.updateCpeConfig(650, fullRequest())

        assertTrue(response.appliedNetwork)
        assertTrue(response.appliedWifi)
        assertEquals(WanManagement.OLT_OMCI, response.networkChannel)
        assertTrue(response.warnings.isEmpty())
        verify(exactly = 1) {
            cpeDeviceAdapter.applyConfiguration(VSOL_SN, null, "GigaFiber-Casa", "clavewifi1")
        }
        assertEquals("192.168.30.50", subscription.ip)
        assertEquals(100, oltOnu.mainVlanId)
        assertEquals("255.255.255.0", oltOnu.subnetMask)
        assertEquals("192.168.30.1", oltOnu.defaultGateway)
        verify(exactly = 1) { subscriptionRepository.save(subscription) }
        verify(exactly = 1) { oltMgrOnuRepository.save(oltOnu) }
    }

    @Test
    fun `V-SOL with only WAN payload updates the OLT without contacting the ACS`() {
        givenSubscription(650, VSOL_SN)
        givenOltOnu(VSOL_SN)
        every { cpeDeviceAdapter.describe(VSOL_SN) } returns vsolDescriptor
        every { oltService.updateOnuWanConfig(VSOL_SN, 100, any(), any(), any(), any(), any()) } returns
            OnuWanUpdateResult(applied = true, uniqueExternalId = "1_1_0_5")

        val response = service.updateCpeConfig(
            650,
            UpdateCpeConfigRequest(network = networkRequest(), wifi = null)
        )

        assertTrue(response.appliedNetwork)
        assertFalse(response.appliedWifi)
        assertEquals(WanManagement.OLT_OMCI, response.networkChannel)
        verify(exactly = 0) { cpeDeviceAdapter.applyConfiguration(any(), any(), any(), any()) }
    }

    @Test
    fun `OLT failures are reported as warnings without breaking the wifi channel`() {
        givenSubscription(650, VSOL_SN)
        every { cpeDeviceAdapter.describe(VSOL_SN) } returns vsolDescriptor
        every { oltService.updateOnuWanConfig(VSOL_SN, any(), any(), any(), any(), any(), any()) } returns
            OnuWanUpdateResult(applied = false, warnings = listOf("SmartOLT respondió 500"))
        every {
            cpeDeviceAdapter.applyConfiguration(VSOL_SN, null, "GigaFiber-Casa", "clavewifi1")
        } returns CpeConfigurationOutcome(appliedNetwork = false, appliedWifi = true)

        val response = service.updateCpeConfig(650, fullRequest())

        assertFalse(response.appliedNetwork)
        assertTrue(response.appliedWifi)
        assertNull(response.networkChannel)
        assertEquals(listOf("SmartOLT respondió 500"), response.warnings)
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    @Test
    fun `TR-069 capable ONU keeps writing WAN through the ACS`() {
        givenSubscription(650, HUAWEI_SN)
        every { cpeDeviceAdapter.describe(HUAWEI_SN) } returns huaweiDescriptor
        every {
            cpeDeviceAdapter.applyConfiguration(HUAWEI_SN, any(), "GigaFiber-Casa", "clavewifi1")
        } returns CpeConfigurationOutcome(appliedNetwork = true, appliedWifi = true)

        val response = service.updateCpeConfig(650, fullRequest())

        assertTrue(response.appliedNetwork)
        assertTrue(response.appliedWifi)
        assertEquals(WanManagement.TR069, response.networkChannel)
        assertTrue(response.warnings.isEmpty())
        verify(exactly = 0) { oltService.updateOnuWanConfig(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `adapter warnings about missing WAN nodes are propagated without failing`() {
        givenSubscription(650, HUAWEI_SN)
        every { cpeDeviceAdapter.describe(HUAWEI_SN) } returns huaweiDescriptor
        every {
            cpeDeviceAdapter.applyConfiguration(HUAWEI_SN, any(), any(), any())
        } returns CpeConfigurationOutcome(
            appliedNetwork = false,
            appliedWifi = true,
            warnings = listOf(CpeWarnings.WAN_NOT_WRITABLE),
        )

        val response = service.updateCpeConfig(650, fullRequest())

        assertFalse(response.appliedNetwork)
        assertTrue(response.appliedWifi)
        assertEquals(listOf(CpeWarnings.WAN_NOT_WRITABLE), response.warnings)
    }

    @Test
    fun `cpe status keeps GPON online when ACS inform is stale`() {
        givenSubscription(650, VSOL_SN)
        givenOltOnu(VSOL_SN)
        every { cpeDeviceAdapter.describe(VSOL_SN) } returns vsolDescriptor.copy(
            lastInform = "2020-01-01T00:00:00.000Z",
            reachable = false,
        )

        val status = service.getCpeStatus(650)

        assertEquals(GponState.ONLINE, status.gponStatus.state)
        assertEquals("-18.4", status.gponStatus.rxDbm)
        assertEquals(AcsState.STALE, status.acsStatus.state)
        assertFalse(status.acsStatus.reachable)
        assertTrue(status.online)
        assertFalse(status.capabilities.canWriteWanViaTr069)
        assertTrue(status.capabilities.canWriteWanViaOmci)
        assertTrue(status.capabilities.canWriteWifiViaTr069)
        assertEquals(WanManagement.OLT_OMCI, status.capabilities.wanManagedBy)
    }

    @Test
    fun `cpe status reports unknown ACS state when device never informed`() {
        givenSubscription(650, HUAWEI_SN)
        givenOltOnu(HUAWEI_SN)
        every { cpeDeviceAdapter.describe(HUAWEI_SN) } returns null

        val status = service.getCpeStatus(650)

        assertEquals(GponState.ONLINE, status.gponStatus.state)
        assertEquals(AcsState.UNKNOWN, status.acsStatus.state)
        assertTrue(status.capabilities.canWriteWanViaTr069)
    }

    private fun givenSubscription(id: Int, sn: String): Subscription {
        val subscription = Subscription(
            id = id,
            equipmentCondition = EquipmentCondition.LOAN,
            fiberOnu = Onu(sn = sn),
        )
        every { subscriptionRepository.findById(id) } returns Optional.of(subscription)
        every { subscriptionRepository.save(subscription) } returns subscription
        return subscription
    }

    private fun givenOltOnu(sn: String): OltMgrOnu {
        val onu = OltMgrOnu(
            id = 10L,
            sn = sn,
            externalId = "gigafiber-ma5608t_1_0_5",
            olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2"),
            board = 1,
            port = 0,
            onuIndex = 5,
        )
        onu.status = OltMgrOnuStatusCurrent(
            onuId = 10L,
            onu = onu,
            runState = "online",
            onuRxDbm = BigDecimal("-18.40"),
            onuTxDbm = BigDecimal("2.10"),
        )
        every { oltMgrOnuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(sn) } returns Optional.of(onu)
        every { oltMgrOnuRepository.save(onu) } returns onu
        return onu
    }

    private fun networkRequest() = CpeNetworkConfigRequest(
        ipAddress = "192.168.30.50",
        subnetMask = "255.255.255.0",
        gateway = "192.168.30.1",
        dnsPrimary = "8.8.8.8",
        dnsSecondary = "8.8.4.4",
        vlanId = 100,
    )

    private fun fullRequest() = UpdateCpeConfigRequest(
        network = networkRequest(),
        wifi = CpeWifiConfigRequest(ssid = "GigaFiber-Casa", password = "clavewifi1"),
    )

    private companion object {
        const val VSOL_SN = "HWTC15F5E946"
        const val HUAWEI_SN = "HWTC15F5CD86"
    }
}
