package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.RouterOsAddRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsCallRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsPrintRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsRemoveRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsSetRequest
import com.dscorp.wispadmin.traffic.service.RouterOsCommandUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TrafficRouterOsControllerTest {

    private val useCase = mockk<RouterOsCommandUseCase>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(TrafficRouterOsController(useCase)).build()

    @Test
    fun `POST print returns rows`() {
        val request = slot<RouterOsPrintRequest>()
        every { useCase.print(8, capture(request)) } returns Result.success(listOf(mapOf(".id" to "*1")))

        mockMvc.post("/api/traffic/v1/routeros/8/print") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"path":"/queue/simple","query":{"target":"10.0.0.1/32"}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.rows[0].['.id']") { value("*1") }
        }
        assertEquals("/queue/simple", request.captured.path)
        assertEquals("10.0.0.1/32", request.captured.query["target"])
    }

    @Test
    fun `POST add set remove and call keep write contract`() {
        every { useCase.add(8, any<RouterOsAddRequest>()) } returns Result.success(Unit)
        every { useCase.set(8, any<RouterOsSetRequest>()) } returns Result.success(Unit)
        every { useCase.remove(8, any<RouterOsRemoveRequest>()) } returns Result.success(Unit)
        every { useCase.call(8, any<RouterOsCallRequest>()) } returns Result.success(
            listOf(mapOf("tx-bits-per-second" to "10")),
        )

        mockMvc.post("/api/traffic/v1/routeros/8/add") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"path":"/ppp/secret","args":{"name":"gf6"}}"""
        }.andExpect { status { isOk() }; jsonPath("$.ok") { value(true) } }

        mockMvc.post("/api/traffic/v1/routeros/8/set") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"path":"/ppp/secret","id":"*2","args":{"disabled":"true"}}"""
        }.andExpect { status { isOk() }; jsonPath("$.ok") { value(true) } }

        mockMvc.post("/api/traffic/v1/routeros/8/remove") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"path":"/ppp/secret","id":"*2"}"""
        }.andExpect { status { isOk() }; jsonPath("$.ok") { value(true) } }

        mockMvc.post("/api/traffic/v1/routeros/8/call") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"path":"/interface/monitor-traffic","args":{"interface":"ether1","once":""}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.rows[0]['tx-bits-per-second']") { value("10") }
        }
    }
}
