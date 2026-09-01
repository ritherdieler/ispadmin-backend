package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.BandwidthNetworkDto
import com.dscorp.wispadmin.traffic.dto.BandwidthOverviewDto
import com.dscorp.wispadmin.traffic.dto.BandwidthRangeDto
import com.dscorp.wispadmin.traffic.dto.BandwidthSeriesDto
import com.dscorp.wispadmin.traffic.service.BandwidthIntelligenceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class BandwidthIntelligenceControllerTest {

    private val service = mockk<BandwidthIntelligenceService>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(BandwidthIntelligenceController(service))
        .build()

    @Test
    fun `GET network devuelve overview y serie en una respuesta`() {
        val from = LocalDateTime.of(2026, 8, 31, 0, 0)
        val to = LocalDateTime.of(2026, 9, 1, 0, 0)
        val meta = BandwidthRangeDto(from.toString(), to.toString(), "1h", 100.0, "FRESH", "GOOD")
        every { service.network(from, to, "auto", 7, 9) } returns BandwidthNetworkDto(
            overview = BandwidthOverviewDto(meta, 1000, 200, 10.0, 2.0, 20.0, 4.0, 30.0, 6.0, 25.0, null, 1, 0),
            series = BandwidthSeriesDto(meta, emptyList())
        )

        mockMvc.get("/traffic/bandwidth/v1/network") {
            param("from", from.toString())
            param("to", to.toString())
            param("routerId", "7")
            param("planId", "9")
        }.andExpect {
            status { isOk() }
            jsonPath("$.overview.totalRxBytes") { value(1000) }
            jsonPath("$.series.meta.resolution") { value("1h") }
        }

        verify(exactly = 1) { service.network(from, to, "auto", 7, 9) }
    }
}
