package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.SubscriptionIntegrityViolationClassifier
import com.dscorp.wispadmin.wispadmin.service.cleanup.CleanupReport
import com.dscorp.wispadmin.wispadmin.service.cleanup.SubscriptionHardCleanupService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SubscriptionHardCleanupAuthTest {
    private val cleanup = mockk<SubscriptionHardCleanupService>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(
        SubscriptionController(
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
            subscriptionProvisionService = mockk(relaxed = true),
            gatewayCpe = mockk(relaxed = true),
            subscriptionAcsLinkService = mockk(relaxed = true),
            environment = GigafiberEnvironmentProperties(),
            hardCleanup = cleanup,
        )
    ).build()

    @Test
    fun `quien no es ADMIN recibe 403`() {
        mockMvc.perform(post("/subscription/42/hard-cleanup"))
            .andExpect(status().isForbidden)
        mockMvc.perform(
            post("/subscription/42/hard-cleanup")
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "SECRETARY")
        ).andExpect(status().isForbidden)
        verify(exactly = 0) { cleanup.cleanup(any()) }
    }

    @Test
    fun `ADMIN dispara la limpieza`() {
        every { cleanup.cleanup(42) } returns CleanupReport(status = "COMPLETE", steps = emptyList())
        mockMvc.perform(
            post("/subscription/42/hard-cleanup")
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN")
                .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)
        verify { cleanup.cleanup(42) }
    }
}
