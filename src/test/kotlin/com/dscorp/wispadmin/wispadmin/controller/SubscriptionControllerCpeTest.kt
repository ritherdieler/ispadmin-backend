package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.wispadmin.cpe.CpeCapabilityResolver
import com.dscorp.wispadmin.wispadmin.cpe.CpeConfigurationOutcome
import com.dscorp.wispadmin.wispadmin.cpe.CpeDeviceAdapter
import com.dscorp.wispadmin.wispadmin.cpe.CpeDeviceDescriptor
import com.dscorp.wispadmin.wispadmin.cpe.CpeWarnings
import com.dscorp.wispadmin.wispadmin.cpe.DefaultCpeCapabilityStrategy
import com.dscorp.wispadmin.wispadmin.cpe.VsolCpeCapabilityStrategy
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.genieacs.GenieAcsDeviceNotFoundException
import com.dscorp.wispadmin.wispadmin.genieacs.CpeDeviceOfflineException
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.CpeConfigValidator
import com.dscorp.wispadmin.wispadmin.service.CpeManagementService
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.OltService
import com.dscorp.wispadmin.wispadmin.service.OnuWanUpdateResult
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIpConflictNocNotifier
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.util.Optional

class SubscriptionControllerCpeTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val oltMgrOnuRepository = mockk<OltMgrOnuRepository>(relaxed = true)
    private val cpeDeviceAdapter = mockk<CpeDeviceAdapter>()
    private val oltService = mockk<OltService>()
    private val cpeManagementService = CpeManagementService(
        subscriptionRepository = subscriptionRepository,
        oltMgrOnuRepository = oltMgrOnuRepository,
        cpeDeviceAdapter = cpeDeviceAdapter,
        oltService = oltService,
        capabilityResolver = CpeCapabilityResolver(
            listOf(VsolCpeCapabilityStrategy(), DefaultCpeCapabilityStrategy())
        ),
        cpeConfigValidator = CpeConfigValidator(),
    )

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            SubscriptionController(
                repository = subscriptionRepository,
                subscriptionService = mockk<SubscriptionService>(relaxed = true),
                placeRepository = mockk(relaxed = true),
                planRepository = mockk(relaxed = true),
                napBoxRepository = mockk(relaxed = true),
                networkDeviceRepository = mockk(relaxed = true),
                couponRepository = mockk(relaxed = true),
                subscriptionLogRepository = mockk(relaxed = true),
                storageService = mockk<FirebaseStorageService>(relaxed = true),
                eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true),
                integrityViolationClassifier = SubscriptionIntegrityViolationClassifier(),
                ipConflictNocNotifier = mockk<SubscriptionIpConflictNocNotifier>(relaxed = true),
                cpeManagementService = cpeManagementService
            )
        )
        .setMessageConverters(MappingJackson2HttpMessageConverter())
        .build()

    @Test
    fun `get cpe-status reports GPON link and ACS sync as independent states`() {
        givenSubscription(650, HUAWEI_SN)
        val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")
        val onu = OltMgrOnu(
            id = 10L,
            sn = HUAWEI_SN,
            externalId = "gigafiber-ma5608t_1_0_5",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 5
        )
        onu.status = OltMgrOnuStatusCurrent(
            onuId = 10L,
            onu = onu,
            runState = "online",
            onuRxDbm = BigDecimal("-18.40")
        )
        every { oltMgrOnuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(HUAWEI_SN) } returns Optional.of(onu)
        every { cpeDeviceAdapter.describe(HUAWEI_SN) } returns huaweiDescriptor()

        mockMvc.perform(get("/subscription/650/cpe-status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.online").value(true))
            .andExpect(jsonPath("$.rxDbm").value("-18.4"))
            .andExpect(jsonPath("$.lastInform").value("2026-08-19T18:00:00.000Z"))
            .andExpect(jsonPath("$.sn").value(HUAWEI_SN))
            .andExpect(jsonPath("$.gponStatus.state").value("ONLINE"))
            .andExpect(jsonPath("$.gponStatus.rxDbm").value("-18.4"))
            .andExpect(jsonPath("$.acsStatus.state").value("SYNCED"))
            .andExpect(jsonPath("$.acsStatus.reachable").value(true))
            .andExpect(jsonPath("$.capabilities.canWriteWanViaTr069").value(true))
            .andExpect(jsonPath("$.capabilities.canWriteWifiViaTr069").value(true))
    }

    @Test
    fun `get cpe-status keeps GPON online while ACS inform is stale on V-SOL`() {
        givenSubscription(650, VSOL_SN)
        val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")
        val onu = OltMgrOnu(
            id = 11L,
            sn = VSOL_SN,
            externalId = "gigafiber-ma5608t_1_0_6",
            olt = olt,
            board = 1,
            port = 0,
            onuIndex = 6
        )
        onu.status = OltMgrOnuStatusCurrent(onuId = 11L, onu = onu, runState = "online")
        every { oltMgrOnuRepository.findBySnIgnoreCaseAndDeletedAtIsNull(VSOL_SN) } returns Optional.of(onu)
        every { cpeDeviceAdapter.describe(VSOL_SN) } returns vsolDescriptor(reachable = false)

        mockMvc.perform(get("/subscription/650/cpe-status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gponStatus.state").value("ONLINE"))
            .andExpect(jsonPath("$.acsStatus.state").value("STALE"))
            .andExpect(jsonPath("$.capabilities.canWriteWanViaTr069").value(false))
            .andExpect(jsonPath("$.capabilities.canWriteWanViaOmci").value(true))
            .andExpect(jsonPath("$.capabilities.canWriteWifiViaTr069").value(true))
            .andExpect(jsonPath("$.capabilities.wanManagedBy").value("OLT_OMCI"))
    }

    @Test
    fun `put wifi rejects subscription without ONU serial`() {
        every { subscriptionRepository.findById(12) } returns Optional.of(
            Subscription(id = 12, equipmentCondition = EquipmentCondition.SOLD)
        )

        mockMvc.perform(
            put("/subscription/12/wifi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ssid":"GigaFiber-Casa","password":"clavewifi1"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("La suscripción no tiene ONU registrada"))

        verify(exactly = 0) { cpeDeviceAdapter.applyConfiguration(any(), any(), any(), any()) }
    }

    @Test
    fun `put wifi sends credentials to the CPE using fiberOnu serial`() {
        givenSubscription(650, HUAWEI_SN)
        every {
            cpeDeviceAdapter.applyConfiguration(HUAWEI_SN, null, "GigaFiber-Casa", "clavewifi1")
        } returns CpeConfigurationOutcome(appliedNetwork = false, appliedWifi = true)

        mockMvc.perform(
            put("/subscription/650/wifi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ssid":"GigaFiber-Casa","password":"clavewifi1"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subscriptionId").value(650))

        verify(exactly = 1) {
            cpeDeviceAdapter.applyConfiguration(HUAWEI_SN, null, "GigaFiber-Casa", "clavewifi1")
        }
    }

    @Test
    fun `put cpe-config applies network and wifi on TR-069 capable ONUs`() {
        givenSubscription(650, HUAWEI_SN)
        every { cpeDeviceAdapter.describe(HUAWEI_SN) } returns huaweiDescriptor()
        every {
            cpeDeviceAdapter.applyConfiguration(
                HUAWEI_SN,
                match { it?.ipAddress == "192.168.30.50" && it.vlanId == 100 },
                "GigaFiber-Casa",
                "clavewifi1",
            )
        } returns CpeConfigurationOutcome(appliedNetwork = true, appliedWifi = true)

        mockMvc.perform(
            put("/subscription/650/cpe-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullPayload())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.appliedNetwork").value(true))
            .andExpect(jsonPath("$.appliedWifi").value(true))
            .andExpect(jsonPath("$.warnings").isEmpty)
    }

    @Test
    fun `put cpe-config routes WAN to the OLT and wifi to the ACS on V-SOL ONUs`() {
        givenSubscription(650, VSOL_SN)
        every { cpeDeviceAdapter.describe(VSOL_SN) } returns vsolDescriptor(reachable = true)
        every {
            oltService.updateOnuWanConfig(
                VSOL_SN, 100, "192.168.30.50", "255.255.255.0", "192.168.30.1", "8.8.8.8", "8.8.4.4"
            )
        } returns OnuWanUpdateResult(applied = true, uniqueExternalId = "1_1_0_5")
        every {
            cpeDeviceAdapter.applyConfiguration(VSOL_SN, null, "GigaFiber-Casa", "clavewifi1")
        } returns CpeConfigurationOutcome(appliedNetwork = false, appliedWifi = true)

        mockMvc.perform(
            put("/subscription/650/cpe-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullPayload())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.appliedNetwork").value(true))
            .andExpect(jsonPath("$.appliedWifi").value(true))
            .andExpect(jsonPath("$.networkChannel").value("OLT_OMCI"))
            .andExpect(jsonPath("$.warnings").isEmpty)

        verify(exactly = 1) {
            cpeDeviceAdapter.applyConfiguration(VSOL_SN, null, "GigaFiber-Casa", "clavewifi1")
        }
    }

    @Test
    fun `put cpe-config reports OLT failures as warnings and keeps wifi working`() {
        givenSubscription(650, VSOL_SN)
        every { cpeDeviceAdapter.describe(VSOL_SN) } returns vsolDescriptor(reachable = true)
        every {
            oltService.updateOnuWanConfig(VSOL_SN, any(), any(), any(), any(), any(), any())
        } returns OnuWanUpdateResult(applied = false, warnings = listOf(CpeWarnings.ONU_NOT_FOUND_IN_OLT))
        every {
            cpeDeviceAdapter.applyConfiguration(VSOL_SN, null, "GigaFiber-Casa", "clavewifi1")
        } returns CpeConfigurationOutcome(appliedNetwork = false, appliedWifi = true)

        mockMvc.perform(
            put("/subscription/650/cpe-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullPayload())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.appliedNetwork").value(false))
            .andExpect(jsonPath("$.appliedWifi").value(true))
            .andExpect(jsonPath("$.warnings[0]").value(CpeWarnings.ONU_NOT_FOUND_IN_OLT))
    }

    @Test
    fun `put cpe-config returns 409 when CPE is offline in ACS`() {
        givenSubscription(650, HUAWEI_SN)
        every { cpeDeviceAdapter.describe(HUAWEI_SN) } returns huaweiDescriptor()
        every {
            cpeDeviceAdapter.applyConfiguration(any(), any(), any(), any())
        } throws CpeDeviceOfflineException(HUAWEI_SN, "2026-08-17T14:00:00.000Z")

        mockMvc.perform(
            put("/subscription/650/cpe-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"wifi":{"ssid":"GigaFiber-Casa","password":"clavewifi1"}}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `put wifi returns 404 when GenieACS does not know the serial`() {
        givenSubscription(650, HUAWEI_SN)
        every {
            cpeDeviceAdapter.applyConfiguration(HUAWEI_SN, null, "GigaFiber-Casa", "clavewifi1")
        } throws GenieAcsDeviceNotFoundException(HUAWEI_SN)

        mockMvc.perform(
            put("/subscription/650/wifi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ssid":"GigaFiber-Casa","password":"clavewifi1"}""")
        )
            .andExpect(status().isNotFound)
    }

    private fun givenSubscription(id: Int, sn: String) {
        val subscription = Subscription(
            id = id,
            equipmentCondition = EquipmentCondition.LOAN,
            fiberOnu = Onu(sn = sn),
        )
        every { subscriptionRepository.findById(id) } returns Optional.of(subscription)
        every { subscriptionRepository.save(subscription) } returns subscription
    }

    private fun huaweiDescriptor() = CpeDeviceDescriptor(
        serialNumber = HUAWEI_SN,
        deviceId = "00259E-EG8145V5-$HUAWEI_SN",
        vendor = "00259E",
        model = "EG8145V5",
        lastInform = "2026-08-19T18:00:00.000Z",
        reachable = true,
    )

    private fun vsolDescriptor(reachable: Boolean) = CpeDeviceDescriptor(
        serialNumber = VSOL_SN,
        deviceId = "B46415-V2804AX15T-12345B46415F5E946",
        vendor = "B46415",
        model = "V2804AX15T",
        lastInform = "2026-08-19T18:00:00.000Z",
        reachable = reachable,
    )

    private fun fullPayload(): String = """
        {
          "network": {
            "ipAddress": "192.168.30.50",
            "subnetMask": "255.255.255.0",
            "gateway": "192.168.30.1",
            "dnsPrimary": "8.8.8.8",
            "dnsSecondary": "8.8.4.4",
            "vlanId": 100
          },
          "wifi": {
            "ssid": "GigaFiber-Casa",
            "password": "clavewifi1"
          }
        }
    """.trimIndent()

    private companion object {
        const val HUAWEI_SN = "HWTC15F5CD86"
        const val VSOL_SN = "HWTC15F5E946"
    }
}
