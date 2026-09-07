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
    fun `reboot uses stored device id`() {
        val records = mockk<CpeRecordRepository>()
        every { records.findById("SN1") } returns Optional.of(CpeRecord(sn = "SN1", deviceId = "dev-1"))
        val client = mockk<GenieAcsClient>()
        every { client.reboot("dev-1", connectionRequest = true) } returns GenieAcsTaskResult(
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
}
