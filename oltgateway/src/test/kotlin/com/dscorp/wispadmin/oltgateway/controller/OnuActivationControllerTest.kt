package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.service.OltServicePortService
import com.dscorp.wispadmin.oltgateway.service.OnuActivationService
import com.dscorp.wispadmin.oltgateway.service.OnuServicePortsDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class OnuActivationControllerTest {

    private val activation = mockk<OnuActivationService>(relaxed = true)
    private val servicePorts = mockk<OltServicePortService>()
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(OnuActivationController(activation, servicePorts))
        .build()

    @Test
    fun `ensure-mgmt registra VLAN 1000 via service port service`() {
        every { servicePorts.ensureMgmtVlan("VSOL0031C0B6", 1000) } returns OnuServicePortsDto(
            sn = "VSOL0031C0B6",
            board = 1,
            port = 6,
            ontId = 116,
            vlans = setOf(100, 1000),
        )

        mockMvc.perform(
            post("/api/olt-gateway/onus/VSOL0031C0B6/service-port/ensure-mgmt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"vlan":1000}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sn").value("VSOL0031C0B6"))
            .andExpect(jsonPath("$.board").value(1))
            .andExpect(jsonPath("$.ontId").value(116))

        verify { servicePorts.ensureMgmtVlan("VSOL0031C0B6", 1000) }
    }
}
