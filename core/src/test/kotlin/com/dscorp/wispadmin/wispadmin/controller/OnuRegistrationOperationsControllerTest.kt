package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.provisioningv2.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class OnuRegistrationOperationsControllerTest {
    private val service = mockk<OnuRegistrationOperationService>()
    private val controller = OnuRegistrationOperationsController(service)
    private val mvc = MockMvcBuilders.standaloneSetup(controller).build()
    private val json = jacksonObjectMapper()

    @Test
    fun `start uses authenticated operator identity not request body`() {
        val request = OnuRegistrationStartRequest("request-0001", "VSOL0031C0B6", target())
        val expected = ProvisioningOperation(
            "operation-1", "lab", null, request.serial,
            flowVersion = 3,
            phase = ProvisioningPhase.WAITING_FOR_ACS,
            operatorId = 71,
            operatorUsername = "lab-tech",
            registrationRequestKey = request.requestKey,
            onuTarget = request.target,
        )
        every { service.start(71, "lab-tech", request) } returns expected

        mvc.perform(post("/onu-registration-operations")
            .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 71L)
            .requestAttr(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE, "lab-tech")
            .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "TECHNICIAN")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(request)))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.operatorId").value(71))

        verify(exactly = 1) { service.start(71, "lab-tech", request) }
    }

    @Test
    fun `registration start is forbidden for non operator roles`() {
        val request = OnuRegistrationStartRequest("request-0002", "VSOL0031C0B6", target())
        mvc.perform(post("/onu-registration-operations")
            .requestAttr(PlatformAuthFilter.AUTH_USER_ID_ATTRIBUTE, 71L)
            .requestAttr(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE, "secretary")
            .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "SECRETARY")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(request)))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { service.start(any(), any(), any()) }
    }

    private fun target() = ProvisioningOnuTarget("olt-1", "GPON", "0", "1", "VSOLVA74", 100)
}
