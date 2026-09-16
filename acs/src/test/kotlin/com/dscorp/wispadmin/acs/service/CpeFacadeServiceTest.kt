package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.CpeProvisionCommand
import com.dscorp.wispadmin.acs.CpeStatus
import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.genieacs.GenieAcsProperties
import com.dscorp.wispadmin.acs.genieacs.GenieAcsTaskResult
import com.dscorp.wispadmin.acs.genieacs.NamedCpeProvisioner
import com.dscorp.wispadmin.acs.genieacs.Tr069ProvisionOutcome
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
        val named = mockk<NamedCpeProvisioner>()
        val properties = GenieAcsProperties().apply { enabled = false }
        val service = CpeFacadeService(records, mockk(relaxed = true), properties, named)

        val result = service.provision(CpeProvisionCommand(sn = "SN1"))

        assertEquals(CpeStatus.NA, result.status)
        verify(exactly = 0) { named.provision(any()) }
    }

    @Test
    fun `provision persists COMPLETE from GenieACS outcome`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("SN1") } returns Optional.of(CpeRecord(sn = "SN1"))
        every { records.save(any()) } answers { firstArg() }
        val named = mockk<NamedCpeProvisioner>()
        every { named.provision(any()) } returns Tr069ProvisionOutcome(
            status = CpeStatus.COMPLETE,
            deviceId = "dev-1",
        )
        val properties = GenieAcsProperties().apply { enabled = true }
        val service = CpeFacadeService(records, mockk(relaxed = true), properties, named)

        val result = service.provision(CpeProvisionCommand(sn = "SN1", uniqueExternalId = "ext-1"))

        assertEquals(CpeStatus.COMPLETE, result.status)
        assertEquals("dev-1", result.deviceId)
    }

    @Test
    fun `STATIC_IP provision routes to named provisioner`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("VSOL0031C0B6") } returns Optional.empty()
        every { records.save(any()) } answers { firstArg() }
        val named = mockk<NamedCpeProvisioner>()
        every { named.provision(any()) } returns Tr069ProvisionOutcome(
            status = CpeStatus.COMPLETE,
            deviceId = "vsol-1",
        )
        val service = CpeFacadeService(
            records,
            mockk(relaxed = true),
            GenieAcsProperties().apply { enabled = true },
            named,
        )

        val result = service.provision(
            CpeProvisionCommand(
                sn = "VSOL0031C0B6",
                onuType = "VSOLVA74",
                ip = "192.168.250.11",
                wanVlanId = 100,
                wifiSsid24 = "lab-vsol-e2e-24",
                wifiPassword24 = "LabVsolWifi24!",
                wifiSsid5 = "lab-vsol-e2e-24 - 5G",
                wifiPassword5 = "LabVsolWifi24!",
            )
        )

        assertEquals(CpeStatus.COMPLETE, result.status)
        verify(exactly = 1) { named.provision(any()) }
    }

    @Test
    fun `provision PPPoE without IP forwards credentials to named provisioner`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("VSOL0031C0B6") } returns Optional.empty()
        every { records.save(any()) } answers { firstArg() }
        val named = mockk<NamedCpeProvisioner>()
        val captured = slot<com.dscorp.wispadmin.acs.genieacs.Tr069ProvisionRequest>()
        every { named.provision(capture(captured)) } returns Tr069ProvisionOutcome(
            status = CpeStatus.PENDING,
            deviceId = "dev-pppoe",
        )
        val service = CpeFacadeService(
            records,
            mockk(relaxed = true),
            GenieAcsProperties().apply { enabled = true },
            named,
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
        val service = CpeFacadeService(records, client, GenieAcsProperties())

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
        val service = CpeFacadeService(records, client, GenieAcsProperties().apply { enabled = true })

        val result = service.telemetry("ZTEGDC47BFFD")

        assertEquals("2026-09-06T17:57:11.717Z", result.lastInformAt)
        assertEquals(java.time.Instant.parse("2026-09-06T17:57:11.717Z"), record.lastInformAt)
        verify(exactly = 1) { records.save(record) }
    }

    @Test
    fun `accessLayout uses named layouts without CSV profile paths`() {
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
        val service = CpeFacadeService(
            records,
            client,
            GenieAcsProperties().apply { enabled = true },
        )

        val layout = service.accessLayout("VSOL0031C0B6")

        assertEquals(true, layout.hasPppPath)
        assertEquals(false, layout.wanIpSharesPppSlot)
        assertEquals(null, layout.wanIpPath)
        assertEquals(null, layout.wanPppPath)
        assertEquals("http://192.168.253.40:7547/", layout.connectionRequestUrl)
    }

    @Test
    fun `pppoe provision routes to named provisioner`() {
        val records = mockk<CpeRecordRepository>(relaxed = true)
        every { records.findById("VSOL0031C0B6") } returns Optional.empty()
        every { records.save(any()) } answers { firstArg() }
        val named = mockk<NamedCpeProvisioner>()
        every { named.provision(any()) } returns Tr069ProvisionOutcome(
            status = CpeStatus.COMPLETE,
            deviceId = "vsol-1",
        )
        val service = CpeFacadeService(
            records,
            mockk(relaxed = true),
            GenieAcsProperties().apply {
                enabled = true
                this.vparams.enabled = true
            },
            named,
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
    }

    @Test
    fun `wifi-refresh only forces a connection request and never reads in parallel`() {
        val records = mockk<CpeRecordRepository>()
        every { records.findById("SN1") } returns Optional.of(CpeRecord(sn = "SN1", deviceId = "dev-1"))
        val client = mockk<GenieAcsClient>()
        every {
            client.enqueueProvisions("dev-1", "gigafiber-wifi-telemetry", emptyList(), connectionRequest = true)
        } returns GenieAcsTaskResult(statusCode = 202, body = "queued", accepted = true)
        val service = CpeFacadeService(
            records,
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
        verify(exactly = 0) { client.getParameterValues(any(), any(), any()) }
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
        val named = mockk<NamedCpeProvisioner>()
        every {
            named.setWifi("VSOL0031C0B6", "lab-24", "lab-5", "11111111")
        } returns com.dscorp.wispadmin.acs.CpeCommandResult(true, CpeStatus.COMPLETE, "WiFi aplicado")
        val service = CpeFacadeService(
            mockk(relaxed = true),
            mockk(relaxed = true),
            GenieAcsProperties().apply { enabled = true },
            named,
        )

        val result = service.setWifi(
            "VSOL0031C0B6",
            com.dscorp.wispadmin.acs.CpeWifiCommand("lab-24", "lab-5", "11111111"),
        )

        assertEquals(true, result.accepted)
        assertEquals(CpeStatus.COMPLETE, result.status)
    }
}
