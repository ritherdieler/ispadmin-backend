package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NetworkDeviceControllerTest {

    private val repository = mockk<NetworkDeviceRepository>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(NetworkDeviceController(repository))
        .build()

    @Test
    fun `getCoreTypes excluye CLOUD_CORE_ROUTER deshabilitados`() {
        val active = NetworkDevice(
            id = 1,
            name = "MK1",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
            disabled = false
        )
        every { repository.findActiveCloudCoreRouters() } returns listOf(active)

        mockMvc.perform(get("/networkDevice/coreTypes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].disabled").value(false))

        verify(exactly = 1) { repository.findActiveCloudCoreRouters() }
        verify(exactly = 0) {
            repository.findByNetworkDeviceType(NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER)
        }
    }

    @Test
    fun `getCoreTypes never exposes router credentials`() {
        val active = NetworkDevice(
            id = 8,
            name = "Mikrotik CCR 2",
            username = "router-admin",
            password = "router-secret",
            ipAddress = "10.0.0.1",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
            vlanId = 100,
            disabled = false
        )
        every { repository.findActiveCloudCoreRouters() } returns listOf(active)

        mockMvc.perform(get("/networkDevice/coreTypes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(8))
            .andExpect(jsonPath("$[0].name").value("Mikrotik CCR 2"))
            .andExpect(jsonPath("$[0].vlanId").value(100))
            .andExpect(jsonPath("$[0].password").doesNotExist())
            .andExpect(jsonPath("$[0].username").doesNotExist())
    }
}
