package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class CpeInspectionServiceTest {
    private val records = mockk<CpeRecordRepository>()
    private val client = mockk<GenieAcsClient>()
    private val archive = mockk<AcsFaultArchive>(relaxed = true)
    private val json = ObjectMapper()
    private val service = CpeInspectionService(records, client, CpeInspectionMapper(), archive)
    private val sn = "ZTEGDC47BFFD"
    private val deviceId = "5872C9-F6600R-ZTEGDC47BFFD"

    @Test
    fun `inspection refuses a device whose serial does not match the subscription ONU`() {
        every { records.findById(sn) } returns Optional.of(CpeRecord(sn = sn, deviceId = deviceId))
        every { client.getDeviceSnapshot(deviceId) } returns json.readTree("""{
          "_id":"$deviceId", "_deviceId":{"_SerialNumber":"ZTEG00000000"}
        }""")

        val error = assertThrows(ResponseStatusException::class.java) { service.summary(sn) }
        assertEquals(409, error.rawStatusCode)
    }

    @Test
    fun `current faults are archived and NBI failure is not shown as an empty list`() {
        every { records.findById(sn) } returns Optional.of(CpeRecord(sn = sn, deviceId = deviceId))
        every { client.getDeviceSnapshot(deviceId) } returns json.readTree("""{
          "_id":"$deviceId", "_deviceId":{"_SerialNumber":"$sn"}
        }""")
        every { client.listFaultDocuments(deviceId) } returns listOf(json.readTree("""{
          "_id":"$deviceId:inform", "device":"$deviceId", "channel":"inform",
          "fault":{"code":"cwmp.9002","message":"password=do-not-show"}
        }"""))

        val current = service.currentFaults(sn)
        assertEquals(1, current.items.size)
        assertFalse(current.items.single().description.contains("do-not-show"))
        verify(exactly = 1) { archive.captureKnownDevice(deviceId, any(), any()) }

        every { client.listFaultDocuments(deviceId) } throws IllegalStateException("NBI unavailable")
        assertThrows(IllegalStateException::class.java) { service.currentFaults(sn) }
    }
}
