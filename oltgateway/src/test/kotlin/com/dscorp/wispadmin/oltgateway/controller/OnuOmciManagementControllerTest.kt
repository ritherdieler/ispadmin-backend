package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.service.OmciManagementEvidence
import com.dscorp.wispadmin.oltgateway.service.OmciManagementTarget
import com.dscorp.wispadmin.oltgateway.service.OmciManagementV2
import com.dscorp.wispadmin.oltgateway.service.ProvisioningV2OnuOwnershipService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class OnuOmciManagementControllerTest {
    private val management = mockk<OmciManagementV2>()
    private val ownerships = mockk<ProvisioningV2OnuOwnershipService>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(
        OnuOmciManagementController(management, ownerships)
    ).build()

    @Test fun `ensures OMCI management WAN for the exact ONU target`() {
        val target = OmciManagementTarget("HWTC9F4BF950", 1, 6, 116, 7)
        every { management.ensure(target) } returns OmciManagementEvidence(true, "10.0.0.5")

        mockMvc.perform(post("/api/olt-gateway/onus/HWTC9F4BF950/omci/management")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"sn":"HWTC9F4BF950","slot":1,"port":6,"ontId":116,"tr069ProfileId":7}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.address").value("10.0.0.5"))

        verify { management.ensure(target) }
    }
}
