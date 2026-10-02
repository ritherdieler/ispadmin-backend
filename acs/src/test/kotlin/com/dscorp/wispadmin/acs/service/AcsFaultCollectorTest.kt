package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class AcsFaultCollectorTest {
    @Test fun `NBI failure never resolves archived faults`() {
        val records = mockk<CpeRecordRepository>()
        val client = mockk<GenieAcsClient>()
        val archive = mockk<AcsFaultArchive>(relaxed = true)
        every { records.findAll() } returns listOf(CpeRecord(sn = "SN1", deviceId = "device-1"))
        every { client.listFaultDocuments() } throws IllegalStateException("NBI unavailable")

        AcsFaultCollector(records, client, CpeInspectionMapper(), archive, true).collect()

        verify(exactly = 0) { archive.capture(any(), any(), any(), any()) }
    }
}
