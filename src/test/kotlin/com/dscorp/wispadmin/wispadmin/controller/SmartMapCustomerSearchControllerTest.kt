package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.dto.SmartMapClientSearchDto
import com.dscorp.wispadmin.wispadmin.service.CollectionVisitService
import com.dscorp.wispadmin.wispadmin.service.SmartMapCustomRouteService
import com.dscorp.wispadmin.wispadmin.service.SmartMapRoadRouteService
import com.dscorp.wispadmin.wispadmin.service.SmartMapService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SmartMapCustomerSearchControllerTest {

    private val smartMapService = mockk<SmartMapService>(relaxed = true)
    private val smartMapRoadRouteService = mockk<SmartMapRoadRouteService>(relaxed = true)
    private val collectionVisitService = mockk<CollectionVisitService>(relaxed = true)
    private val smartMapCustomRouteService = mockk<SmartMapCustomRouteService>()

    private val mockMvc = MockMvcBuilders.standaloneSetup(
        SmartMapController(
            smartMapService = smartMapService,
            smartMapRoadRouteService = smartMapRoadRouteService,
            collectionVisitService = collectionVisitService,
            smartMapCustomRouteService = smartMapCustomRouteService,
        ),
    ).build()

    @Test
    fun `GET customers search by DNI returns 200 with client DTO`() {
        every { smartMapCustomRouteService.searchCustomers("71822055", 20) } returns listOf(
            SmartMapClientSearchDto(
                id = 42,
                customerName = "Cliente Demo",
                abonadoCode = "42",
                status = ServiceStatus.ACTIVE,
                address = "Av. Principal 100",
                latitude = -11.24,
                longitude = -77.38,
                debtAmount = 50.0,
                placeName = "Tiw",
                hasValidLocation = true,
            ),
        )

        mockMvc.perform(
            get("/smart-map/customers/search")
                .param("query", "71822055")
                .accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(42))
            .andExpect(jsonPath("$[0].customerName").value("Cliente Demo"))
            .andExpect(jsonPath("$[0].abonadoCode").value("42"))
            .andExpect(jsonPath("$[0].hasValidLocation").value(true))
            .andExpect(jsonPath("$[0].latitude").value(-11.24))
            .andExpect(jsonPath("$[0].longitude").value(-77.38))
    }

    @Test
    fun `GET customers search with blank query returns 200 empty list without calling service`() {
        mockMvc.perform(
            get("/smart-map/customers/search")
                .param("query", " ")
                .accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$").isEmpty)

        verify(exactly = 0) { smartMapCustomRouteService.searchCustomers(any(), any()) }
    }

    @Test
    fun `GET customers search without query param returns 200 empty list`() {
        mockMvc.perform(
            get("/smart-map/customers/search")
                .accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)

        verify(exactly = 0) { smartMapCustomRouteService.searchCustomers(any(), any()) }
    }

    @Test
    fun `GET customers search with short query returns 200 empty list`() {
        mockMvc.perform(
            get("/smart-map/customers/search")
                .param("query", "7")
                .accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)

        verify(exactly = 0) { smartMapCustomRouteService.searchCustomers(any(), any()) }
    }
}
