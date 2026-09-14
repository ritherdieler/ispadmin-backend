package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.CpeProvisionCommand
import com.dscorp.wispadmin.acs.CpeStatus
import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.genieacs.GenieAcsProperties
import com.dscorp.wispadmin.acs.genieacs.GenieAcsTaskResult
import com.dscorp.wispadmin.acs.genieacs.Tr069ProvisionOutcome
import com.dscorp.wispadmin.acs.genieacs.Tr069ProvisioningService
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class CpeFacadeServiceTest {

    @Test
    fun `disabled ACS marks NA without calling provisioner`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("SN1") } returns Optional.empty()
        every { records.save(any()) } answers { firstArg() }
        val provisioning = mockk<Tr069ProvisioningService>()
        val properties = GenieAcsProperties().apply { enabled = false }
        val service = CpeFacadeService(records, provisioning, mockk(relaxed = true), properties)

        val result = service.provision(CpeProvisionCommand(sn = "SN1"))

        assertEquals(CpeStatus.NA, result.status)
        verify(exactly = 0) { provisioning.provision(any()) }
    }

    @Test
    fun `provision persists COMPLETE from GenieACS outcome`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("SN1") } returns Optional.of(CpeRecord(sn = "SN1"))
        every { records.save(any()) } answers { firstArg() }
        val provisioning = mockk<Tr069ProvisioningService>()
        every { provisioning.provision(any()) } returns Tr069ProvisionOutcome(
            status = CpeStatus.COMPLETE,
            deviceId = "dev-1",
        )
        val properties = GenieAcsProperties().apply { enabled = true }
        val service = CpeFacadeService(records, provisioning, mockk(relaxed = true), properties)

        val result = service.provision(CpeProvisionCommand(sn = "SN1", uniqueExternalId = "ext-1"))

        assertEquals(CpeStatus.COMPLETE, result.status)
        assertEquals("dev-1", result.deviceId)
    }

    @Test
    fun `provision PPPoE without IP forwards credentials to named provisioner`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("VSOL0031C0B6") } returns Optional.empty()
        every { records.save(any()) } answers { firstArg() }
        val named = mockk<com.dscorp.wispadmin.acs.genieacs.NamedCpeProvisioner>()
        val captured = slot<com.dscorp.wispadmin.acs.genieacs.Tr069ProvisionRequest>()
        every { named.provision(capture(captured)) } returns Tr069ProvisionOutcome(
            status = CpeStatus.PENDING,
            deviceId = "dev-pppoe",
        )
        val service = CpeFacadeService(
            records,
            mockk(relaxed = true),
            mockk(relaxed = true),
            GenieAcsProperties().apply { enabled = true },
            namedProvisioner = named,
        )

        service.provision(
            CpeProvisionCommand(
                sn = "VSOL0031C0B6",
                onuType = "V2804AX15T",
                wanVlanId = 100,
                pppoeUsername = "gf2397",
                pppoePassword = "secreto123",
            )
        )

        assertEquals("gf2397", captured.captured.pppoeUsername)
        assertEquals("secreto123", captured.captured.pppoePassword)
        assertEquals(true, captured.captured.usesPppoe())
    }

    @Test
    fun `reboot uses stored device id`() {
        val records = mockk<CpeRecordRepository>()
        every { records.findById("SN1") } returns Optional.of(CpeRecord(sn = "SN1", deviceId = "dev-1"))
        val client = mockk<GenieAcsClient>()
        every {
            client.enqueueProvisions("dev-1", "gf-reboot-poc", emptyList(), connectionRequest = true)
        } returns GenieAcsTaskResult(
            statusCode = 202,
            body = "ok",
            accepted = true,
        )
        val service = CpeFacadeService(
            records,
            mockk(relaxed = true),
            client,
            GenieAcsProperties(),
        )

        val result = service.reboot("SN1")

        assertEquals(true, result.accepted)
        assertEquals(CpeStatus.PENDING, result.status)
    }

    @Test
    fun `telemetry enriches lastInformAt from GenieACS and persists it`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        val record = CpeRecord(sn = "ZTEGDC47BFFD", deviceId = "5872C9-F6600R-ZTEGDC47BFFD", status = CpeStatus.COMPLETE)
        every { records.findById("ZTEGDC47BFFD") } returns Optional.of(record)
        every { records.save(any()) } answers { firstArg() }
        val client = mockk<GenieAcsClient>()
        every { client.findDeviceBySerialSuffix("47BFFD") } returns listOf(
            com.dscorp.wispadmin.acs.genieacs.GenieAcsDevice(
                id = "5872C9-F6600R-ZTEGDC47BFFD",
                serialNumber = "ZTEGDC47BFFD",
                lastInform = "2026-09-06T17:57:11.717Z",
            )
        )
        val service = CpeFacadeService(records, mockk(relaxed = true), client, GenieAcsProperties().apply { enabled = true })

        val result = service.telemetry("ZTEGDC47BFFD")

        assertEquals("2026-09-06T17:57:11.717Z", result.lastInformAt)
        assertEquals(java.time.Instant.parse("2026-09-06T17:57:11.717Z"), record.lastInformAt)
        verify(exactly = 1) { records.save(record) }
    }

    @Test
    fun `accessLayout reports shared WAN slot from the TR-069 profile`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("VSOL0031C0B6") } returns Optional.empty()
        val client = mockk<GenieAcsClient>()
        every { client.findDeviceBySerialSuffix("31C0B6") } returns listOf(
            com.dscorp.wispadmin.acs.genieacs.GenieAcsDevice(
                id = "vsol-1",
                serialNumber = "VSOL0031C0B6",
                productClass = "V2804AX15T",
                lastInform = "2026-09-11T14:00:00Z",
                connectionRequestUrl = "http://192.168.253.40:7547/",
            )
        )
        val profiles = mockk<com.dscorp.wispadmin.acs.genieacs.Tr069ModelProfileRegistry>()
        every { profiles.resolve(null, "V2804AX15T") } returns com.dscorp.wispadmin.acs.genieacs.Tr069ModelProfile(
            productClass = "V2804AX15T",
            wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
            wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
            clientWanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            clientWanPppConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2",
        )
        val service = CpeFacadeService(
            records,
            mockk(relaxed = true),
            client,
            GenieAcsProperties().apply { enabled = true },
            profiles,
        )

        val layout = service.accessLayout("VSOL0031C0B6")

        assertEquals(true, layout.hasPppPath)
        assertEquals(false, layout.wanIpSharesPppSlot)
        assertEquals(null, layout.wanIpPath)
        assertEquals("http://192.168.253.40:7547/", layout.connectionRequestUrl)
    }

    @Test
    fun `pppoe provision routes to named provisioner not vparams`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("VSOL0031C0B6") } returns Optional.empty()
        every { records.save(any()) } answers { firstArg() }
        val pathProvisioner = mockk<Tr069ProvisioningService>()
        val vparamProvisioner = mockk<com.dscorp.wispadmin.acs.genieacs.VparamProvisioner>()
        val named = mockk<com.dscorp.wispadmin.acs.genieacs.NamedCpeProvisioner>()
        every { named.provision(any()) } returns Tr069ProvisionOutcome(
            status = CpeStatus.COMPLETE,
            deviceId = "vsol-1",
        )
        val properties = GenieAcsProperties().apply {
            enabled = true
            this.vparams.enabled = true
        }
        val service = CpeFacadeService(
            records,
            pathProvisioner,
            mockk(relaxed = true),
            properties,
            vparamProvisioner = vparamProvisioner,
            namedProvisioner = named,
        )

        val result = service.provision(
            CpeProvisionCommand(
                sn = "VSOL0031C0B6",
                pppoeUsername = "gf2398",
                pppoePassword = "secret123",
            )
        )

        assertEquals(CpeStatus.COMPLETE, result.status)
        verify(exactly = 1) { named.provision(any()) }
        verify(exactly = 0) { vparamProvisioner.provision(any()) }
        verify(exactly = 0) { pathProvisioner.provision(any()) }
    }

    @Test
    fun `vparams accessLayout reports hasPppPath by product class with null paths`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("VSOL0031C0B6") } returns Optional.empty()
        val client = mockk<GenieAcsClient>()
        every { client.findDeviceBySerialSuffix("31C0B6") } returns listOf(
            com.dscorp.wispadmin.acs.genieacs.GenieAcsDevice(
                id = "vsol-1",
                serialNumber = "VSOL0031C0B6",
                productClass = "V2804AX15T",
                lastInform = "2026-09-11T14:00:00Z",
                connectionRequestUrl = "http://10.20.0.2:7547/",
            )
        )
        val profiles = mockk<com.dscorp.wispadmin.acs.genieacs.Tr069ModelProfileRegistry>(relaxed = true)
        val service = CpeFacadeService(
            records,
            mockk(relaxed = true),
            client,
            GenieAcsProperties().apply {
                enabled = true
                vparams.enabled = true
            },
            profiles,
        )

        val layout = service.accessLayout("VSOL0031C0B6")

        assertEquals(true, layout.hasPppPath)
        assertEquals(null, layout.wanIpPath)
        assertEquals(null, layout.wanPppPath)
        assertEquals("http://10.20.0.2:7547/", layout.connectionRequestUrl)
        verify(exactly = 0) { profiles.resolve(any(), any()) }
    }

    @Test
    fun `vparams wifi-refresh GPVs GfWifiStatus and does not refresh WLAN object`() {
        val records = mockk<CpeRecordRepository>()
        every { records.findById("SN1") } returns Optional.of(CpeRecord(sn = "SN1", deviceId = "dev-1"))
        val client = mockk<GenieAcsClient>()
        every {
            client.getParameterValues("dev-1", listOf("VirtualParameters.GfWifiStatus"), connectionRequest = true)
        } returns GenieAcsTaskResult(statusCode = 202, body = "queued", accepted = true)
        val service = CpeFacadeService(
            records,
            mockk(relaxed = true),
            client,
            GenieAcsProperties().apply {
                enabled = true
                vparams.enabled = true
            },
        )

        val result = service.wifiRefresh("SN1")

        assertEquals(true, result.accepted)
        assertEquals(CpeStatus.PENDING, result.status)
        verify(exactly = 0) { client.refreshObject(any(), any(), any()) }
    }

    @Test
    fun `reboot enqueues gf-reboot-poc even when vparams enabled`() {
        val records = mockk<CpeRecordRepository>()
        every { records.findById("SN1") } returns Optional.of(CpeRecord(sn = "SN1", deviceId = "dev-1"))
        val client = mockk<GenieAcsClient>()
        every {
            client.enqueueProvisions("dev-1", "gf-reboot-poc", emptyList(), connectionRequest = true)
        } returns GenieAcsTaskResult(statusCode = 202, body = "ok", accepted = true)
        val service = CpeFacadeService(
            records,
            mockk(relaxed = true),
            client,
            GenieAcsProperties().apply {
                enabled = true
                vparams.enabled = true
            },
        )

        val result = service.reboot("SN1")

        assertEquals(true, result.accepted)
        assertEquals(CpeStatus.PENDING, result.status)
        verify(exactly = 0) { client.reboot(any(), any()) }
        verify(exactly = 0) { client.setParameterValues(any(), any(), any()) }
    }

    @Test
    fun `setWifi forwards to named provisioner`() {
        val named = mockk<com.dscorp.wispadmin.acs.genieacs.NamedCpeProvisioner>()
        every {
            named.setWifi("VSOL0031C0B6", "lab-24", "lab-5", "11111111")
        } returns com.dscorp.wispadmin.acs.CpeCommandResult(true, CpeStatus.COMPLETE, "WiFi aplicado")
        val service = CpeFacadeService(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            GenieAcsProperties().apply { enabled = true },
            namedProvisioner = named,
        )

        val result = service.setWifi(
            "VSOL0031C0B6",
            com.dscorp.wispadmin.acs.CpeWifiCommand("lab-24", "lab-5", "11111111"),
        )

        assertEquals(true, result.accepted)
        assertEquals(CpeStatus.COMPLETE, result.status)
    }
}
