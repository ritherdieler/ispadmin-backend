package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.genieacs.GenieAcsClient
import com.dscorp.wispadmin.wispadmin.genieacs.GenieAcsDeviceNotFoundException
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.CpeManagementService
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
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
    private val oltMgrOnuRepository = mockk<OltMgrOnuRepository>()
    private val genieAcsClient = mockk<GenieAcsClient>()
    private val cpeManagementService = CpeManagementService(
        subscriptionRepository = subscriptionRepository,
        oltMgrOnuRepository = oltMgrOnuRepository,
        genieAcsClient = genieAcsClient
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
    fun `get cpe-status combines OLT runState rx power and GenieACS lastInform`() {
        val subscription = Subscription(
            id = 650,
            equipmentCondition = EquipmentCondition.LOAN,
            fiberOnu = Onu(sn = "HWTC15F5CD86")
        )
        val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")
        val onu = OltMgrOnu(
            id = 10L,
            sn = "HWTC15F5CD86",
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
        every { subscriptionRepository.findById(650) } returns Optional.of(subscription)
        every { oltMgrOnuRepository.findBySnIgnoreCaseAndDeletedAtIsNull("HWTC15F5CD86") } returns Optional.of(onu)
        every { genieAcsClient.getLastInform("HWTC15F5CD86") } returns "2026-08-17T14:00:00.000Z"

        mockMvc.perform(get("/subscription/650/cpe-status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.online").value(true))
            .andExpect(jsonPath("$.rxDbm").value("-18.4"))
            .andExpect(jsonPath("$.lastInform").value("2026-08-17T14:00:00.000Z"))
            .andExpect(jsonPath("$.sn").value("HWTC15F5CD86"))
    }

    @Test
    fun `put wifi rejects subscription without ONU serial`() {
        val subscription = Subscription(
            id = 12,
            equipmentCondition = EquipmentCondition.SOLD
        )
        every { subscriptionRepository.findById(12) } returns Optional.of(subscription)

        mockMvc.perform(
            put("/subscription/12/wifi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ssid":"GigaFiber-Casa","password":"clavewifi1"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("La suscripción no tiene ONU registrada"))

        verify(exactly = 0) { genieAcsClient.updateWifi(any(), any(), any()) }
    }

    @Test
    fun `put wifi sends credentials to GenieACS using fiberOnu serial`() {
        val subscription = Subscription(
            id = 650,
            equipmentCondition = EquipmentCondition.LOAN,
            fiberOnu = Onu(sn = "HWTC15F5CD86")
        )
        every { subscriptionRepository.findById(650) } returns Optional.of(subscription)
        every { genieAcsClient.updateWifi("HWTC15F5CD86", "GigaFiber-Casa", "clavewifi1") } returns Unit

        mockMvc.perform(
            put("/subscription/650/wifi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ssid":"GigaFiber-Casa","password":"clavewifi1"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subscriptionId").value(650))

        verify(exactly = 1) { genieAcsClient.updateWifi("HWTC15F5CD86", "GigaFiber-Casa", "clavewifi1") }
    }

    @Test
    fun `put wifi returns 404 when GenieACS does not know the serial`() {
        val subscription = Subscription(
            id = 650,
            equipmentCondition = EquipmentCondition.LOAN,
            fiberOnu = Onu(sn = "HWTC15F5CD86")
        )
        every { subscriptionRepository.findById(650) } returns Optional.of(subscription)
        every {
            genieAcsClient.updateWifi("HWTC15F5CD86", "GigaFiber-Casa", "clavewifi1")
        } throws GenieAcsDeviceNotFoundException("HWTC15F5CD86")

        mockMvc.perform(
            put("/subscription/650/wifi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ssid":"GigaFiber-Casa","password":"clavewifi1"}""")
        )
            .andExpect(status().isNotFound)
    }
}
