package com.dscorp.wispadmin.wispadmin.trafficclient
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
class TrafficRouterProviderTest {
    @Test fun `internal router directory requires service key and hides credentials without it`() {
        val repo=mockk<NetworkDeviceRepository>()
        every { repo.findAll(any<Pageable>()) } returns PageImpl(listOf(NetworkDevice(2,"router",password="fixture",username="test",ipAddress="192.0.2.2")))
        val filter=CoreTrafficApiKeyFilter(TrafficClientProperties().apply { apiKey="key" },jacksonObjectMapper())
        val mvc=MockMvcBuilders.standaloneSetup(TrafficRouterDirectoryController(repo)).addFilters<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(filter).build()
        mvc.get("/internal/traffic/routers").andExpect { status { isUnauthorized() }; jsonPath("$.items") { doesNotExist() } }
        mvc.get("/internal/traffic/routers") { header("X-Traffic-Key","key") }.andExpect { status { isOk() }; jsonPath("$.items[0].id") { value(2) }; jsonPath("$.totalPages") { value(1) } }
    }
}
