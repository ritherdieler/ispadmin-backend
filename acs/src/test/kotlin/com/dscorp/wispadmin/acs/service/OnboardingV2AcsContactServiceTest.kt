package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.OnboardingV2ContactRequest
import com.dscorp.wispadmin.acs.OnboardingV2ContactState
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.genieacs.GenieAcsDevice
import com.dscorp.wispadmin.acs.genieacs.GenieAcsProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class OnboardingV2AcsContactServiceTest {

    private val client = mockk<GenieAcsClient>()
    private val service = OnboardingV2AcsContactService(client, GenieAcsProperties().apply { enabled = true })

    @Test
    fun `a unique device sharing the last six serial characters is ready`() {
        val device = GenieAcsDevice(
            id = "B46415-V2804AX15T-12345B4641531C0B6",
            serialNumber = "12345B4641531C0B6",
            productClass = "V2804AX15T",
            softwareVersion = "V1.0.1",
        )
        every { client.findDeviceBySerialSuffix("VSOL0031C0B6") } returns listOf(device)
        every { client.findDeviceBySerialSuffix("31C0B6") } returns listOf(device)

        val response = service.contact(OnboardingV2ContactRequest("op-12345678", "VSOL0031C0B6"))

        assertEquals(OnboardingV2ContactState.READY, response.state)
        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", response.deviceId)
        assertEquals("V2804AX15T", response.model)
    }

    @Test
    fun `two devices sharing the last six serial characters are not chosen`() {
        val devices = listOf(
            GenieAcsDevice(id = "B46415-V2804AX15T-12345B4641531C0B6", serialNumber = "12345B4641531C0B6", productClass = "V2804AX15T", softwareVersion = "V1.0.1"),
            GenieAcsDevice(id = "OTHER-V2804AX15T-9999931C0B6", serialNumber = "9999931C0B6", productClass = "V2804AX15T", softwareVersion = "V1.0.1"),
        )
        every { client.findDeviceBySerialSuffix("VSOL0031C0B6") } returns devices
        every { client.findDeviceBySerialSuffix("31C0B6") } returns devices

        val error = org.junit.jupiter.api.assertThrows<ResponseStatusException> {
            service.contact(OnboardingV2ContactRequest("op-12345678", "VSOL0031C0B6"))
        }

        assertEquals(HttpStatus.CONFLICT, error.status)
    }
}
