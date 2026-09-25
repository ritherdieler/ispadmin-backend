package com.dscorp.wispadmin.wispadmin.oltclient

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LabOnuFacadeControllerTest {

    private val client = mockk<OltGatewayHttpClient>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(LabOnuFacadeController(client)).build()

    @Test
    fun `la fachada solo existe en staging con cliente de gateway`() {
        val expression = LabOnuFacadeController::class.java
            .getAnnotation(ConditionalOnExpression::class.java)
            .value
        assertEquals(
            "\${olt.gateway.client-enabled:false} && '\${gigafiber.environment.tag:}' == 'stg'",
            expression,
        )
    }

    @Test
    fun `listar agregar y quitar reenvian al gateway`() {
        every { client.getJson("/api/olt-gateway/onu/lab") } returns ResponseEntity.ok(
            """[{"sn":"ZTEGDC47BFFD"}]""",
        )
        every { client.postJsonBody("/api/olt-gateway/onu/lab", """{"sn":"HWTCNEWLAB01"}""") } returns ResponseEntity.ok(
            """{"sn":"HWTCNEWLAB01"}""",
        )
        every { client.deleteJson("/api/olt-gateway/onu/lab/HWTCNEWLAB01") } returns ResponseEntity.noContent().build()

        mockMvc.get("/onu/lab").andExpect {
            status { isOk() }
            jsonPath("$[0].sn") { value("ZTEGDC47BFFD") }
        }
        mockMvc.post("/onu/lab") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sn":"HWTCNEWLAB01"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.sn") { value("HWTCNEWLAB01") }
        }
        mockMvc.delete("/onu/lab/HWTCNEWLAB01").andExpect {
            status { isNoContent() }
        }

        verify { client.getJson("/api/olt-gateway/onu/lab") }
        verify { client.postJsonBody("/api/olt-gateway/onu/lab", """{"sn":"HWTCNEWLAB01"}""") }
        verify { client.deleteJson("/api/olt-gateway/onu/lab/HWTCNEWLAB01") }
    }
}
