package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.service.ObsDatabaseQueryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ObservabilityDatabaseControllerTest {

    private val databaseQueryService = mockk<ObsDatabaseQueryService>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ObservabilityDatabaseController(databaseQueryService, "America/Lima"))
        .build()

    @Test
    fun `propaga el filtro route a top queries`() {
        every {
            databaseQueryService.topQueries(any(), any(), any(), any(), "/api/subscriptions")
        } returns emptyList()

        mockMvc.perform(
            get("/observability/database/queries").param("route", "/api/subscriptions")
        ).andExpect(status().isOk)

        verify {
            databaseQueryService.topQueries(any(), any(), any(), null, "/api/subscriptions")
        }
    }

    @Test
    fun `propaga el filtro route a nplusone`() {
        every {
            databaseQueryService.nPlusOne(any(), any(), any(), any(), "/api/subscriptions")
        } returns emptyList()

        mockMvc.perform(
            get("/observability/database/nplusone").param("route", "/api/subscriptions")
        ).andExpect(status().isOk)

        verify {
            databaseQueryService.nPlusOne(any(), any(), any(), null, "/api/subscriptions")
        }
    }
}
