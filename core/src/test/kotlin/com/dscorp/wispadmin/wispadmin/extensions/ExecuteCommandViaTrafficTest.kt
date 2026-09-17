package com.dscorp.wispadmin.wispadmin.extensions

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.trafficclient.TrafficRouterOsCommandUseCase
import com.dscorp.wispadmin.wispadmin.trafficclient.TrafficRouterOsGatewayAccessor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExecuteCommandViaTrafficTest {

    @AfterEach
    fun tearDown() {
        TrafficRouterOsGatewayAccessor.clear()
        NetworkDeviceConnectionManager.clearHelper()
    }

    @Test
    fun `executeCommand prints through Traffic gateway not MikrotikClient`() {
        val useCase = mockk<TrafficRouterOsCommandUseCase>()
        every { useCase.print(8, "/queue/simple", emptyMap(), emptyList()) } returns Result.success(
            listOf(mapOf("name" to "id:1")),
        )
        TrafficRouterOsGatewayAccessor.setUseCase(useCase)
        val device = NetworkDevice(id = 8, name = "MK2", ipAddress = "10.0.0.1")

        var rows = emptyList<Map<String, String>>()
        device.executeCommand { session ->
            rows = session.print("/queue/simple")
        }

        assertEquals("id:1", rows.first()["name"])
        verify(exactly = 1) { useCase.print(8, "/queue/simple", emptyMap(), emptyList()) }
    }
}
