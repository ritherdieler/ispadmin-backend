package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.service.NetworkDeviceConnectionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NetworkDeviceConnectionControllerTest {

    private val repository = mockk<NetworkDeviceRepository>()
    private val connectionService = mockk<NetworkDeviceConnectionService>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(NetworkDeviceConnectionController(repository, connectionService))
        .build()

    @Test
    fun `getCloudCoreRouters excluye CLOUD_CORE_ROUTER deshabilitados`() {
        val active = NetworkDevice(
            id = 1,
            name = "MK1",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
            disabled = false
        )
        every { repository.findActiveCloudCoreRouters() } returns listOf(active)

        mockMvc.perform(get("/networkDevice/connection/cloud-core-routers"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].disabled").value(false))

        verify(exactly = 1) { repository.findActiveCloudCoreRouters() }
        verify(exactly = 0) {
            repository.findByNetworkDeviceType(NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER)
        }
    }
}
