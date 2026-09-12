package com.dscorp.wispadmin.acs.genieacs

import com.dscorp.wispadmin.acs.CpeStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class VparamProvisionerTest {

    @Test
    fun `pppoe provision SPVs GfApplyInternetPppoe JSON and verifies GfInternetStatus`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(
                id = "vsol-1",
                serialNumber = "VSOL0031C0B6",
                productClass = "V2804AX15T",
                lastInform = "2026-09-11T14:00:00Z",
            )
        )
        every { client.setParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getDeviceParameterValue("vsol-1", "VirtualParameters.GfInternetStatus") } returns
            """{"connected":true,"ip":"10.64.0.22","productClass":"V2804AX15T"}"""
        val provisioner = VparamProvisioner(
            client,
            GenieAcsProperties().apply {
                enabled = true
                vparams.enabled = true
            },
        )

        val outcome = provisioner.provision(
            Tr069ProvisionRequest(
                onuSerial = "VSOL0031C0B6",
                onuTypeName = "V2804AX15T",
                ip = null,
                ipSegment = null,
                wifiSsid24 = null,
                wifiPassword24 = null,
                wifiSsid5 = null,
                wifiPassword5 = null,
                wanVlanId = 100,
                pppoeUsername = "gf2398",
                pppoePassword = "secret123",
            )
        )

        assertEquals(CpeStatus.COMPLETE, outcome.status)
        verify {
            client.setParameterValues(
                "vsol-1",
                match { values ->
                    values.size == 1 &&
                        values[0].path == "VirtualParameters.GfApplyInternetPppoe" &&
                        values[0].value.contains("\"username\":\"gf2398\"") &&
                        values[0].value.contains("\"password\":\"secret123\"")
                },
                connectionRequest = true,
            )
        }
        verify(exactly = 0) {
            client.setParameterValues(
                any(),
                match { values -> values.any { it.path.contains("WANPPPConnection") } },
                any(),
            )
        }
        verify { client.getParameterValues("vsol-1", listOf("VirtualParameters.GfInternetStatus"), true) }
    }

    @Test
    fun `wifi credentials trigger GfSetWifi and GfWifiStatus`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(
                id = "vsol-1",
                serialNumber = "VSOL0031C0B6",
                productClass = "V2804AX15T",
            )
        )
        every { client.setParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getDeviceParameterValue("vsol-1", "VirtualParameters.GfInternetStatus") } returns
            """{"connected":true,"ip":"10.64.0.22"}"""
        every { client.getDeviceParameterValue("vsol-1", "VirtualParameters.GfWifiStatus") } returns
            """{"ssid24":"GIGA-24","ssid5":"GIGA-5"}"""
        val provisioner = VparamProvisioner(client, GenieAcsProperties().apply { enabled = true })

        val outcome = provisioner.provision(
            Tr069ProvisionRequest(
                onuSerial = "VSOL0031C0B6",
                onuTypeName = "V2804AX15T",
                ip = null,
                ipSegment = null,
                wifiSsid24 = "GIGA-24",
                wifiPassword24 = "pass-24xx",
                wifiSsid5 = "GIGA-5",
                wifiPassword5 = "pass-5xxx",
                wanVlanId = 100,
                pppoeUsername = "gf2398",
                pppoePassword = "secret123",
            )
        )

        assertEquals(CpeStatus.COMPLETE, outcome.status)
        verify {
            client.setParameterValues(
                "vsol-1",
                match { values ->
                    values.single().path == "VirtualParameters.GfSetWifi" &&
                        values.single().value.contains("GIGA-24")
                },
                connectionRequest = true,
            )
        }
    }

    @Test
    fun `pppoe does not COMPLETE when observed IP is leftover static WAN`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(
                id = "5872C9-F6600R-ZTEGDC47BFFD",
                serialNumber = "ZTEGDC47BFFD",
                productClass = "F6600R",
                lastInform = "2026-09-11T21:00:00Z",
            )
        )
        every { client.setParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getDeviceParameterValue("5872C9-F6600R-ZTEGDC47BFFD", "VirtualParameters.GfInternetStatus") } returns
            """{"connected":true,"ip":"192.168.250.22","productClass":"F6600R"}"""
        val now = AtomicLong(0)
        val provisioner = VparamProvisioner(
            client,
            GenieAcsProperties().apply {
                enabled = true
                vparams.enabled = true
                waitTimeoutMs = 20
                pollIntervalMs = 5
            },
        ).withTimeControls(clock = { now.get() }, sleeper = { now.addAndGet(it) })

        val outcome = provisioner.provision(
            Tr069ProvisionRequest(
                onuSerial = "ZTEGDC47BFFD",
                onuTypeName = "F6600R",
                ip = null,
                ipSegment = null,
                wifiSsid24 = null,
                wifiPassword24 = null,
                wifiSsid5 = null,
                wifiPassword5 = null,
                wanVlanId = 100,
                pppoeUsername = "gflabzte",
                pppoePassword = "secret123",
            )
        )

        assertNotEquals(CpeStatus.COMPLETE, outcome.status)
        assertEquals(CpeStatus.PENDING, outcome.status)
    }

    @Test
    fun `pppoe does not COMPLETE when SPV is HTTP 202`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(
                id = "5872C9-F6600R-ZTEGDC47BFFD",
                serialNumber = "ZTEGDC47BFFD",
                productClass = "F6600R",
            )
        )
        every { client.setParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 202,
            body = """{"name":"setParameterValues","fault":{"code":"9005"}}""",
            accepted = true,
        )
        val now = AtomicLong(0)
        val provisioner = VparamProvisioner(
            client,
            GenieAcsProperties().apply {
                enabled = true
                vparams.enabled = true
                waitTimeoutMs = 20
                pollIntervalMs = 5
            },
        ).withTimeControls(clock = { now.get() }, sleeper = { now.addAndGet(it) })

        val outcome = provisioner.provision(
            Tr069ProvisionRequest(
                onuSerial = "ZTEGDC47BFFD",
                onuTypeName = "F6600R",
                ip = null,
                ipSegment = null,
                wifiSsid24 = null,
                wifiPassword24 = null,
                wifiSsid5 = null,
                wifiPassword5 = null,
                wanVlanId = 100,
                pppoeUsername = "gflabzte",
                pppoePassword = "secret123",
            )
        )

        assertEquals(CpeStatus.FAILED, outcome.status)
        verify(exactly = 0) {
            client.getParameterValues(any(), any(), any())
        }
    }

    @Test
    fun `flag off is not used by this provisioner contract`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        val provisioner = VparamProvisioner(client, GenieAcsProperties().apply { enabled = true })
        assertTrue(GfVirtualParameters.supportedPppProductClass("V2804AX15T"))
        assertTrue(GfVirtualParameters.supportedPppProductClass("F6600R"))
        assertEquals(false, GfVirtualParameters.supportedPppProductClass("HG8145X6"))
    }
}
