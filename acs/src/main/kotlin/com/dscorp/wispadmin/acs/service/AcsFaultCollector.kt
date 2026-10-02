package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant

class AcsFaultCollector(
    private val records: CpeRecordRepository,
    private val client: GenieAcsClient,
    private val mapper: CpeInspectionMapper,
    private val archive: AcsFaultArchive,
    private val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(AcsFaultCollector::class.java)

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    fun collect() {
        if (!enabled) return
        val linked = records.findAll().filter { !it.deviceId.isNullOrBlank() }
        if (linked.isEmpty()) return
        val faults = try {
            client.listFaultDocuments().map(mapper::fault).groupBy { it.deviceId }
        } catch (ex: Exception) {
            log.warn("GenieACS fault collection unavailable: {}", ex.javaClass.simpleName)
            return
        }
        val at = Instant.now()
        for (record in linked) {
            try {
                archive.capture(record.deviceId!!, record.sn, faults[record.deviceId].orEmpty(), at)
            } catch (ex: Exception) {
                log.warn("Could not archive ACS faults for {}: {}", record.deviceId, ex.javaClass.simpleName)
            }
        }
    }
}
