package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.traffic.dto.RouterOsAddRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsCallRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsPrintRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsRemoveRequest
import com.dscorp.wispadmin.traffic.dto.RouterOsSetRequest
import com.dscorp.wispadmin.traffic.entity.TrafficRouter
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

class RouterOsCommandUseCaseTest {

    private val routers = mockk<TrafficRouterRepository>()
    private val mikrotik = mockk<MikrotikClient>()
    private val session = mockk<MikrotikSession>(relaxed = true)
    private val properties = RouterOsClientProperties().apply { rest.port = 443 }
    private val useCase = RouterOsCommandUseCase(routers, mikrotik, properties)
    private val router = TrafficRouter(id = 8, name = "MK2", host = "10.0.0.1", username = "u", password = "p")

    @Test
    fun `print uses TrafficRouter credentials and session print`() {
        stubSession()
        every {
            session.print("/queue/simple", mapOf("target" to "10.0.0.1/32"), emptyList())
        } returns listOf(mapOf(".id" to "*1", "name" to "id:1"))

        val result = useCase.print(
            8,
            RouterOsPrintRequest(path = "/queue/simple", query = mapOf("target" to "10.0.0.1/32")),
        )

        assertTrue(result.isSuccess)
        assertEquals("*1", result.getOrThrow().first()[".id"])
        verify(exactly = 1) {
            session.print("/queue/simple", mapOf("target" to "10.0.0.1/32"), emptyList())
        }
    }

    @Test
    fun `add set remove and call execute on the same RouterOS session`() {
        stubSession()
        every {
            session.call("/interface/monitor-traffic", mapOf("interface" to "ether1", "once" to ""))
        } returns listOf(mapOf("rx-bits-per-second" to "100"))

        assertTrue(useCase.add(8, RouterOsAddRequest("/ppp/secret", mapOf("name" to "gf6"))).isSuccess)
        assertTrue(useCase.set(8, RouterOsSetRequest("/ppp/secret", "*2", mapOf("disabled" to "true"))).isSuccess)
        assertTrue(useCase.remove(8, RouterOsRemoveRequest("/ppp/secret", "*2")).isSuccess)
        val call = useCase.call(
            8,
            RouterOsCallRequest("/interface/monitor-traffic", mapOf("interface" to "ether1", "once" to "")),
        )

        assertTrue(call.isSuccess)
        assertEquals("100", call.getOrThrow().first()["rx-bits-per-second"])
        verify { session.add("/ppp/secret", mapOf("name" to "gf6")) }
        verify { session.set("/ppp/secret", "*2", mapOf("disabled" to "true")) }
        verify { session.remove("/ppp/secret", "*2") }
    }

    @Test
    fun `missing router is failure and does not open RouterOS`() {
        every { routers.findById(9) } returns Optional.empty()

        val result = useCase.print(9, RouterOsPrintRequest(path = "/queue/simple"))

        assertTrue(result.isFailure)
        verify(exactly = 0) { mikrotik.withSession(any<MikrotikDeviceRef>(), any<(MikrotikSession) -> Any>()) }
    }

    private fun stubSession() {
        val deviceRef = slot<MikrotikDeviceRef>()
        every { routers.findById(8) } returns Optional.of(router)
        every {
            mikrotik.withSession(capture(deviceRef), any<(MikrotikSession) -> Any>())
        } answers {
            assertEquals("8", deviceRef.captured.id)
            assertEquals("10.0.0.1", deviceRef.captured.host)
            assertEquals("u", deviceRef.captured.username)
            assertEquals("p", deviceRef.captured.password)
            val block = invocation.args[1] as (MikrotikSession) -> Any
            block(session)
        }
    }
}
