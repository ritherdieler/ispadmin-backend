package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.genieacs.Tr069SerialMatcher
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class CpeCurrentFaults(
    val deviceId: String,
    val checkedAt: String,
    val items: List<CpeFaultView>,
)

@Service
class CpeInspectionService(
    private val records: CpeRecordRepository,
    private val client: GenieAcsClient,
    private val mapper: CpeInspectionMapper,
    private val archive: AcsFaultArchive,
) {
    fun summary(sn: String): CpeInspectionSummary = mapper.summary(snapshot(sn))

    fun tree(sn: String, parent: String?, query: String?): CpeTreePage =
        mapper.tree(snapshot(sn), parent, query)

    fun currentFaults(sn: String): CpeCurrentFaults {
        val device = snapshot(sn)
        val deviceId = device.path("_id").asText()
        val faults = client.listFaultDocuments(deviceId).map(mapper::fault)
        val checkedAt = Instant.now()
        archive.captureKnownDevice(deviceId, faults, checkedAt)
        return CpeCurrentFaults(deviceId, checkedAt.toString(), faults)
    }

    fun faultHistory(sn: String, page: Int, size: Int): CpeFaultHistoryPage =
        archive.page(record(sn).deviceId!!, page, size)

    private fun snapshot(sn: String): JsonNode {
        val record = record(sn)
        val deviceId = record.deviceId!!
        val device = client.getDeviceSnapshot(deviceId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "GenieACS device not found")
        val expected = Tr069SerialMatcher.normalizeSuffix(record.sn)
        val actual = Tr069SerialMatcher.normalizeSuffix(device.path("_deviceId").path("_SerialNumber").asText(null))
            ?: Tr069SerialMatcher.normalizeSuffix(deviceId.substringAfterLast('-'))
        if (expected == null || actual != expected || device.path("_id").asText() != deviceId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "ACS device identity mismatch")
        }
        return device
    }

    private fun record(sn: String): CpeRecord {
        val normalized = sn.trim().uppercase()
        val record = records.findById(normalized).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "ACS device not linked")
        if (record.deviceId.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "ACS device identity missing")
        }
        return record
    }
}
