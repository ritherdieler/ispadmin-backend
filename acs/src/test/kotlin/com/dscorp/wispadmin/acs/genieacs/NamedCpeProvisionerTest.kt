package com.dscorp.wispadmin.acs.genieacs

import com.dscorp.wispadmin.acs.CpeStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class NamedCpeProvisionerTest {

    @Test
    fun `F6600R enqueues gf-pppoe-wan2-poc with pass24 on both bands then COMPLETE after GPV`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(
                id = "5872C9-F6600R-ZTEGDC47BFFD",
                serialNumber = "ZTEGDC47BFFD",
                productClass = "F6600R",
            )
        )
        every { client.enqueueProvisions(any(), any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every {
            client.getDeviceParameterValue(
                "5872C9-F6600R-ZTEGDC47BFFD",
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.ExternalIPAddress",
            )
        } returns "10.64.0.22"
        every {
            client.getDeviceParameterValue(
                "5872C9-F6600R-ZTEGDC47BFFD",
                "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID",
            )
        } returns "lab-zte-24"
        every {
            client.getDeviceParameterValue(
                "5872C9-F6600R-ZTEGDC47BFFD",
                "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID",
            )
        } returns "lab-zte-5"
        val provisioner = NamedCpeProvisioner(client, GenieAcsProperties().apply { enabled = true })

        val outcome = provisioner.provision(
            Tr069ProvisionRequest(
                onuSerial = "ZTEGDC47BFFD",
                onuTypeName = "F6600R",
                ip = null,
                ipSegment = null,
                wifiSsid24 = "lab-zte-24",
                wifiPassword24 = "from24pass",
                wifiSsid5 = "lab-zte-5",
                wifiPassword5 = "other",
                wanVlanId = 100,
                pppoeUsername = "gflabzte",
                pppoePassword = "secret123",
            )
        )

        assertEquals(CpeStatus.COMPLETE, outcome.status)
        verify {
            client.enqueueProvisions(
                "5872C9-F6600R-ZTEGDC47BFFD",
                NamedGenieAcsProvisions.PPPOE,
                match { args ->
                    args.size == 8 &&
                        args[0] == "gflabzte" &&
                        args[1] == "secret123" &&
                        args[2] == "100" &&
                        args[3] == "2_INTERNET_R_VID_100" &&
                        args[4] == "lab-zte-24" &&
                        args[5] == "from24pass" &&
                        args[6] == "lab-zte-5" &&
                        args[7] == "from24pass"
                },
                connectionRequest = true,
            )
        }
        verify(exactly = 0) { client.setParameterValues(any(), any(), any()) }
    }

    @Test
    fun `HTTP 202 enqueue still waits for GPV and returns COMPLETE`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(
                id = "5872C9-F6600R-ZTEGDC47BFFD",
                serialNumber = "ZTEGDC47BFFD",
                productClass = "F6600R",
            )
        )
        every { client.enqueueProvisions(any(), any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 202,
            body = """{"name":"provisions"}""",
            accepted = true,
        )
        every { client.getParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every {
            client.getDeviceParameterValue(
                "5872C9-F6600R-ZTEGDC47BFFD",
                "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2.ExternalIPAddress",
            )
        } returns "10.64.0.22"
        every {
            client.getDeviceParameterValue(
                "5872C9-F6600R-ZTEGDC47BFFD",
                "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID",
            )
        } returns "lab-zte-24"
        every {
            client.getDeviceParameterValue(
                "5872C9-F6600R-ZTEGDC47BFFD",
                "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID",
            )
        } returns "lab-zte-5"
        val provisioner = NamedCpeProvisioner(client, GenieAcsProperties().apply { enabled = true })

        val outcome = provisioner.provision(
            Tr069ProvisionRequest(
                onuSerial = "ZTEGDC47BFFD",
                onuTypeName = "F6600R",
                ip = null,
                ipSegment = null,
                wifiSsid24 = "lab-zte-24",
                wifiPassword24 = "from24pass",
                wifiSsid5 = "lab-zte-5",
                wifiPassword5 = "other",
                wanVlanId = 100,
                pppoeUsername = "gflabzte",
                pppoePassword = "secret123",
            )
        )

        assertEquals(CpeStatus.COMPLETE, outcome.status)
        verify(atLeast = 1) {
            client.getParameterValues("5872C9-F6600R-ZTEGDC47BFFD", any(), true)
        }
    }

    @Test
    fun `setWifi HTTP 202 still waits for SSID GPV`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(id = "vsol-1", serialNumber = "VSOL0031C0B6", productClass = "V2804AX15T")
        )
        every { client.enqueueProvisions(any(), any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 202,
            body = "accepted",
            accepted = true,
        )
        every { client.getParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every {
            client.getDeviceParameterValue("vsol-1", "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID")
        } returns "vsol"
        every {
            client.getDeviceParameterValue("vsol-1", "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID")
        } returns "vsol-5"
        val provisioner = NamedCpeProvisioner(client, GenieAcsProperties().apply { enabled = true })

        val result = provisioner.setWifi("VSOL0031C0B6", "vsol", "vsol-5", "11111111")

        assertEquals(true, result.accepted)
        assertEquals(CpeStatus.COMPLETE, result.status)
        verify(atLeast = 1) { client.getParameterValues("vsol-1", any(), true) }
    }

    @Test
    fun `unsupported product class fails without enqueue`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(id = "huawei-1", serialNumber = "HW123456", productClass = "HG8145V5")
        )
        val provisioner = NamedCpeProvisioner(client, GenieAcsProperties().apply { enabled = true })

        val outcome = provisioner.provision(
            Tr069ProvisionRequest(
                onuSerial = "HW123456",
                onuTypeName = "HG8145V5",
                ip = null,
                ipSegment = null,
                wifiSsid24 = null,
                wifiPassword24 = null,
                wifiSsid5 = null,
                wifiPassword5 = null,
                wanVlanId = 100,
                pppoeUsername = "gf1",
                pppoePassword = "secret123",
            )
        )

        assertEquals(CpeStatus.FAILED, outcome.status)
        assertTrue(outcome.message!!.contains("unsupported productClass"))
        verify(exactly = 0) { client.enqueueProvisions(any(), any(), any(), any()) }
    }

    @Test
    fun `setWifi enqueues gf-wifi-ssid-poc`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(id = "vsol-1", serialNumber = "VSOL0031C0B6", productClass = "V2804AX15T")
        )
        every { client.enqueueProvisions(any(), any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every {
            client.getDeviceParameterValue("vsol-1", "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID")
        } returns "vsol"
        every {
            client.getDeviceParameterValue("vsol-1", "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID")
        } returns "vsol-5"
        val provisioner = NamedCpeProvisioner(client, GenieAcsProperties().apply { enabled = true })

        val result = provisioner.setWifi("VSOL0031C0B6", "vsol", "vsol-5", "11111111")

        assertEquals(true, result.accepted)
        assertEquals(CpeStatus.COMPLETE, result.status)
        verify {
            client.enqueueProvisions(
                "vsol-1",
                NamedGenieAcsProvisions.WIFI,
                listOf("vsol", "vsol-5", "11111111"),
                connectionRequest = true,
            )
        }
    }

    @Test
    fun `STATIC_IP enqueues gf-wifi-ssid-poc for VSOL layout then COMPLETE after SSIDs`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(
                id = "B46415-V2804AX15T-12345B4641531C0B6",
                serialNumber = "VSOL0031C0B6",
                productClass = "V2804AX15T",
            )
        )
        every { client.enqueueProvisions(any(), any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 202,
            body = "ok",
            accepted = true,
        )
        every { client.getParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every {
            client.getDeviceParameterValue(
                "B46415-V2804AX15T-12345B4641531C0B6",
                "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID",
            )
        } returns "lab-vsol-e2e-24"
        every {
            client.getDeviceParameterValue(
                "B46415-V2804AX15T-12345B4641531C0B6",
                "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID",
            )
        } returns "lab-vsol-e2e-24 - 5G"
        val provisioner = NamedCpeProvisioner(client, GenieAcsProperties().apply { enabled = true })

        val outcome = provisioner.provision(
            Tr069ProvisionRequest(
                onuSerial = "VSOL0031C0B6",
                onuTypeName = "VSOLVA74",
                ip = "192.168.250.11",
                ipSegment = "192.168.250.1/24",
                wifiSsid24 = "lab-vsol-e2e-24",
                wifiPassword24 = "LabVsolWifi24!",
                wifiSsid5 = "lab-vsol-e2e-24 - 5G",
                wifiPassword5 = "other",
                wanVlanId = 100,
            )
        )

        assertEquals(CpeStatus.COMPLETE, outcome.status)
        verify(exactly = 0) {
            client.enqueueProvisions(any(), NamedGenieAcsProvisions.PPPOE, any(), any())
        }
        verify {
            client.enqueueProvisions(
                "B46415-V2804AX15T-12345B4641531C0B6",
                NamedGenieAcsProvisions.WIFI,
                listOf("lab-vsol-e2e-24", "lab-vsol-e2e-24 - 5G", "LabVsolWifi24!"),
                connectionRequest = true,
            )
        }
    }

    @Test
    fun `setWifi rejects passphrase shorter than 8`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        val provisioner = NamedCpeProvisioner(client, GenieAcsProperties().apply { enabled = true })

        val result = provisioner.setWifi("VSOL0031C0B6", "vsol", "vsol-5", "short")

        assertEquals(false, result.accepted)
        assertEquals(CpeStatus.FAILED, result.status)
        verify(exactly = 0) { client.listDevices() }
        verify(exactly = 0) { client.enqueueProvisions(any(), any(), any(), any()) }
    }

    @Test
    fun `provision times out to PENDING when GPV never shows pool IP`() {
        val client = mockk<GenieAcsClient>(relaxed = true)
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(id = "vsol-1", serialNumber = "VSOL0031C0B6", productClass = "V2804AX15T")
        )
        every { client.enqueueProvisions(any(), any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getParameterValues(any(), any(), any()) } returns GenieAcsTaskResult(
            statusCode = 200,
            body = "ok",
            accepted = true,
        )
        every { client.getDeviceParameterValue(any(), any()) } returns null
        val clock = AtomicLong(0)
        val provisioner = NamedCpeProvisioner(
            client,
            GenieAcsProperties().apply {
                enabled = true
                waitTimeoutMs = 10
                pollIntervalMs = 5
            },
        ).withTimeControls(clock = { clock.get() }, sleeper = { clock.addAndGet(it) })

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

        assertEquals(CpeStatus.PENDING, outcome.status)
    }
}
