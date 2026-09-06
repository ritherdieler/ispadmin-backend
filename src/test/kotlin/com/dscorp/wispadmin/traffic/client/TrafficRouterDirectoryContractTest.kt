package com.dscorp.wispadmin.traffic.client

import com.dscorp.wispadmin.traffic.config.*
import com.dscorp.wispadmin.traffic.entity.TrafficRouter
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestTemplate

class TrafficRouterDirectoryContractTest {
    @Test fun `routers are synchronized over HTTP and removed routers are disabled`() {
        val http=RestTemplate()
        val server=MockRestServiceServer.createServer(http)
        server.expect(requestTo("http://core/internal/traffic/routers?page=0&size=200"))
            .andExpect(header("X-Traffic-Key","key"))
            .andRespond(withSuccess("""{"items":[{"id":2,"name":"router","host":"192.0.2.2","username":"test","password":"fixture","enabled":true}],"totalPages":1}""",MediaType.APPLICATION_JSON))
        val props=TrafficProperties().apply { coreBaseUrl="http://core";apiKey="key" }
        val client=HttpTrafficRouterDirectoryClient(props,jacksonObjectMapper(),http)
        val repo=mockk<TrafficRouterRepository>()
        val old=TrafficRouter(id=1)
        every { repo.findAll() } returns listOf(old)
        every { repo.saveAll(any<List<TrafficRouter>>()) } answers { firstArg<List<TrafficRouter>>() }
        TrafficRouterSeedRunner(repo,props,client).synchronize()
        verify { repo.saveAll(match<List<TrafficRouter>> { rows -> rows.any { it.id==1 && !it.enabled } && rows.any { it.id==2 && it.host=="192.0.2.2" } }) }
        server.verify()
    }
    @Test fun `failed directory never disables the stored fleet`() {
        val client=mockk<HttpTrafficRouterDirectoryClient>()
        every { client.list() } throws IllegalStateException("offline")
        val repo=mockk<TrafficRouterRepository>(relaxed=true)
        TrafficRouterSeedRunner(repo,TrafficProperties(),client).synchronize()
        verify(exactly=0) { repo.saveAll(any<List<TrafficRouter>>()) }
    }
}
