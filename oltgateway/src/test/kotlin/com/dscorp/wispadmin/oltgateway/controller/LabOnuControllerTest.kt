package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.config.GatewayCallContext
import com.dscorp.wispadmin.oltgateway.service.InMemoryLabOnuRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LabOnuControllerTest {

    private val registry = InMemoryLabOnuRegistry()
    private val mockMvc = MockMvcBuilders.standaloneSetup(LabOnuController(registry)).build()

    @AfterEach
    fun clear() {
        GatewayCallContext.clear()
    }

    @Test
    fun `prod no lista ni edita el registro`() {
        mockMvc.get("/api/olt-gateway/onu/lab").andExpect { status { isForbidden() } }
        mockMvc.post("/api/olt-gateway/onu/lab") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sn":"HWTCNEWLAB01"}"""
        }.andExpect { status { isForbidden() } }
        assertFalse(registry.isLab("HWTCNEWLAB01"))
    }

    @Test
    fun `staging agrega lista y quita un serial`() {
        GatewayCallContext.setLabInventoryOnly(true)

        mockMvc.post("/api/olt-gateway/onu/lab") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sn":"hwtcnewlab01"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.sn") { value("HWTCNEWLAB01") }
        }

        mockMvc.get("/api/olt-gateway/onu/lab").andExpect {
            status { isOk() }
            jsonPath("$[0].sn") { value("HWTCNEWLAB01") }
        }
        assertTrue(registry.isLab("HWTCNEWLAB01"))

        mockMvc.delete("/api/olt-gateway/onu/lab/HWTCNEWLAB01").andExpect {
            status { isNoContent() }
        }
        assertEquals(0, registry.list().size)
    }
}
