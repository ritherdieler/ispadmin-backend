package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SubscriptionControllerDniCheckTest {

    private val repository = mockk<SubscriptionRepository>()
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
        subscriptionProvisionService = mockk(relaxed = true),
        gatewayCpe = mockk(relaxed = true),
        subscriptionAcsLinkService = mockk(relaxed = true),
    )
    private val mvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `dni check returns only counts of existing subscriptions`() {
        every { repository.countByDni("12345678") } returns 3
        every { repository.countByDniAndServiceStatus("12345678", ServiceStatus.ACTIVE) } returns 2

        mvc.perform(get("/subscription/dni-check").param("dni", " 12345678 "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalSubscriptions").value(3))
            .andExpect(jsonPath("$.activeSubscriptions").value(2))
            .andExpect(jsonPath("$.firstName").doesNotExist())
    }

    @Test
    fun `dni check rejects blank dni`() {
        mvc.perform(get("/subscription/dni-check").param("dni", "  "))
            .andExpect(status().isBadRequest)
    }
}
