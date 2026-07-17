package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredItemDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.exception.OltGatewayExceptionHandler
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import com.dscorp.wispadmin.oltgateway.service.OltManagerFacade
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SmartOltCompatControllerTest {

    private val facade = mockk<OltManagerFacade>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(SmartOltCompatController(facade))
        .setControllerAdvice(OltGatewayExceptionHandler())
        .build()

    @Test
    fun `unconfigured_onus alias`() {
        every { facade.unconfiguredOnus() } returns SmartOltUnconfiguredOnusResponseDto(
            response = listOf(SmartOltUnconfiguredItemDto(sn = "4857544311E70E9A", board = "0", port = "2")),
            status = true
        )

        mockMvc.perform(get("/api/olt-gateway/onu/unconfigured_onus"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.response[0].sn").value("4857544311E70E9A"))
    }

    @Test
    fun `get_onus_details_by_sn alias`() {
        every { facade.getOnusDetailsBySn("4857544311E70E9A") } returns SmartOltOnuBySnResponseDto(
            status = true,
            response_code = "200",
            onus = emptyList()
        )

        mockMvc.perform(get("/api/olt-gateway/onu/get_onus_details_by_sn/4857544311E70E9A"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(true))
    }

    @Test
    fun `authorize_onu alias`() {
        every { facade.authorizeOnu(any()) } returns SmartOltActionResponseDto(
            status = true,
            unique_external_id = "gigafiber-ma5608t_0_2_7"
        )

        mockMvc.perform(
            post("/api/olt-gateway/onu/authorize_onu")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("sn", "4857544311E70E9A")
                .param("board", "0")
                .param("port", "2")
                .param("vlan", "100")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.unique_external_id").value("gigafiber-ma5608t_0_2_7"))

        verify { facade.authorizeOnu(any()) }
    }

    @Test
    fun `move delete reboot aliases`() {
        every { facade.moveOnu(any(), any()) } returns SmartOltActionResponseDto(status = true)
        every { facade.deleteOnu(any()) } returns SmartOltActionResponseDto(status = true)
        every { facade.rebootOnu(any()) } returns SmartOltActionResponseDto(status = true)

        mockMvc.perform(
            post("/api/olt-gateway/onu/move/4857544311E70E9A")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("board", "1")
                .param("port", "0")
        ).andExpect(status().isOk)

        mockMvc.perform(post("/api/olt-gateway/onu/delete/gigafiber-ma5608t_1_0_5"))
            .andExpect(status().isOk)

        mockMvc.perform(post("/api/olt-gateway/onu/reboot/gigafiber-ma5608t_1_0_5"))
            .andExpect(status().isOk)
    }

    @Test
    fun `reboot 404`() {
        every { facade.rebootOnu("missing") } throws OnuNotFoundException("ONU not found")

        mockMvc.perform(post("/api/olt-gateway/onu/reboot/missing"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("onu_not_found"))
    }
}
