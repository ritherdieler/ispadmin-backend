package com.dscorp.wispadmin.wispadmin.oltclient

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class OnuFacadeControllerTest {

    private val client = mockk<OltGatewayHttpClient>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(OnuFacadeController(client)).build()

    @Test
    fun `configured proxies gateway inventory API`() {
        every { client.getJson("/api/olt-gateway/onus/configured", "page=0&size=50") } returns ResponseEntity.ok(
            """{"items":[{"sn":"HWTC1"}],"page":0,"size":50,"totalElements":1,"totalPages":1}"""
        )

        mockMvc.get("/onu/configured") {
            param("page", "0")
            param("size", "50")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.items[0].sn") { value("HWTC1") }
        }
    }

    @Test
    fun `ensure-mgmt proxies gateway service-port API`() {
        every {
            client.postJsonBody(
                "/api/olt-gateway/onus/VSOL0031C0B6/service-port/ensure-mgmt",
                """{"vlan":1000}""",
            )
        } returns ResponseEntity.ok(
            """{"sn":"VSOL0031C0B6","board":1,"port":6,"ontId":116,"vlans":[100,1000]}""",
        )

        mockMvc.post("/onu/VSOL0031C0B6/service-port/ensure-mgmt") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"vlan":1000}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.sn") { value("VSOL0031C0B6") }
            jsonPath("$.ontId") { value(116) }
            jsonPath("$.vlans[0]") { exists() }
        }
    }
}
