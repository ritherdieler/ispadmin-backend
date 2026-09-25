package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.OltService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class OnuSmartOltControllerDeleteAuthTest {
    private val oltService = mockk<OltService>(relaxed = true)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(OnuSmartOltController(oltService)).build()

    @Test
    fun `delete por externalId y por SN rechazan a quien no es ADMIN`() {
        mockMvc.perform(delete("/onu/configured/ext-1"))
            .andExpect(status().isForbidden)
        mockMvc.perform(
            delete("/onu/configured/ext-1")
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "SECRETARY")
        ).andExpect(status().isForbidden)
        mockMvc.perform(delete("/onu/configured/by-sn/HWTC1"))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { oltService.deleteOnu(any()) }
        verify(exactly = 0) { oltService.deleteOnuBySn(any()) }
    }

    @Test
    fun `ADMIN borra por externalId y por SN`() {
        mockMvc.perform(
            delete("/onu/configured/ext-1")
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN")
                .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk)
        mockMvc.perform(
            delete("/onu/configured/by-sn/hwtc1")
                .requestAttr(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE, "ADMIN")
        ).andExpect(status().isOk)

        verify { oltService.deleteOnu("ext-1") }
        verify { oltService.deleteOnuBySn("hwtc1") }
    }
}
