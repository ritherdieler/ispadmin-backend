package com.dscorp.wispadmin.wispadmin.search

import com.dscorp.wispadmin.wispadmin.dto.PageResponseDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionSearchReindexer
import com.dscorp.wispadmin.wispadmin.search.application.SubscriptionSearchService
import com.dscorp.wispadmin.wispadmin.search.controller.SubscriptionSearchController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SubscriptionSearchControllerTest {

    private val service = mockk<SubscriptionSearchService>()
    private val reindexer = mockk<SubscriptionSearchReindexer>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(SubscriptionSearchController(service, reindexer))
        .build()

    @Test
    fun `retorna la forma paginada con items de SubscriptionDto`() {
        val page = PageResponseDto(
            items = listOf(
                SubscriptionDto(id = 2, firstName = "Bruno", lastName = "Perez"),
                SubscriptionDto(id = 1, firstName = "Ana", lastName = "Perez")
            ),
            page = 0,
            size = 20,
            total = 2,
            totalPages = 1
        )
        every { service.search("perez", null, 0, 20) } returns page

        mockMvc.perform(get("/subscription/search").param("q", "perez"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].id").value(2))
            .andExpect(jsonPath("$.items[0].firstName").value("Bruno"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.totalPages").value(1))
    }

    @Test
    fun `propaga el filtro por estado al servicio`() {
        every { service.search("ana", "ACTIVE", 0, 20) } returns
            PageResponseDto(emptyList(), 0, 20, 0, 0)

        mockMvc.perform(
            get("/subscription/search")
                .param("q", "ana")
                .param("status", "ACTIVE")
        ).andExpect(status().isOk)

        verify { service.search("ana", "ACTIVE", 0, 20) }
    }
}
