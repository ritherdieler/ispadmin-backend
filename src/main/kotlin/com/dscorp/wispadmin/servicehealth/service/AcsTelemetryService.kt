package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.Duration

@Service
class AcsTelemetryService(
    private val properties: ServiceHealthProperties, private val client: GenieAcsClient,
    private val subscriptions: SubscriptionRepository, private val acs: SubscriptionAcsRepository,
    private val identity: IdentityService, private val counts: WifiCountSampleRepository,
    private val stations: WifiStationSampleRepository, private val current: WifiCurrentRepository,
    private val cursors: HealthCursorRepository, private val runs: TelemetryRunRepository,
    private val profiles: ReadCapabilityProfileRepository, private val tx: TransactionTemplate
) {
    @Scheduled(fixedDelayString="\${service.health.acs-interval-ms:120000}",initialDelayString="\${service.health.acs-initial-delay-ms:45000}")
    fun poll() {
        if(!properties.enabled || !properties.acsEnabled || properties.pilotSubscriptionIds.isEmpty()) return
        // Serialize across backend instances. The lock is also the durable watcher checkpoint boundary.
        try { tx.executeWithoutResult {
            if(cursors.lock("acs-watcher") == null) return@executeWithoutResult
            val now=Instant.now()
            val run=runs.save(TelemetryRun(source="ACS",equipmentKey="genieacs",startedAt=now))
            try {
                val ids=properties.pilotSubscriptionIds.flatMap { id ->
                    listOfNotNull(acs.findById(id).orElse(null)?.genieacsDeviceId,
                        subscriptions.findById(id).orElse(null)?.tr069DeviceId)
                }.plus(properties.pilotAcsDeviceIds).distinct()
                for(batch in ids.chunked(50)) {
                    val devices=client.readDeviceCache(batch,WifiTelemetry.projection())
                    run.missingCount+=batch.size-devices.size
                    for(device in devices) {
                        run.readCount++
                        val deviceId=device.path("_id").asText()
                        val subId=identity.resolveAcsForCollection(deviceId)
                        if(subId==null) { run.unmappedCount++; continue }
                        if(!properties.collects(subId)) continue
                        val inform=WifiTelemetry.parseInstant(device.path("_lastInform"))
                        if(inform==null) { run.missingCount++; continue }
                        val snapshot=acs.findById(subId).orElse(null)
                        val model=device.path("_deviceId").path("_ProductClass").asText(snapshot?.productClass ?: "")
                        val manufacturer=device.path("_deviceId").path("_Manufacturer").asText(snapshot?.manufacturer ?: "").uppercase()
                        val firmware=WifiTelemetry.value(device,"InternetGatewayDevice.DeviceInfo.SoftwareVersion") ?: snapshot?.softwareVersion ?: "unknown"
                        var profile=profiles.findByManufacturerAndModelAndFirmware(manufacturer,model,firmware)
                        if(profile==null) profile=profiles.save(ReadCapabilityProfile(manufacturer=manufacturer,model=model,firmware=firmware,
                            wifiCount=WifiTelemetry.radios(model).isNotEmpty(),wifiSignal=WifiTelemetry.radios(model).isNotEmpty(),
                            verifiedAt=null))
                        if(snapshot!=null) {
                            snapshot.lastInformAt=inform.atOffset(ZoneOffset.UTC).toLocalDateTime()
                            snapshot.productClass=model; snapshot.softwareVersion=firmware
                            snapshot.updatedAt=now.atOffset(ZoneOffset.UTC).toLocalDateTime(); acs.save(snapshot)
                        }
                        val parsed=WifiTelemetry.parse(device,subId,if(profile.wifiCount) model else "",now,properties.stationHmacKey)

                        if(parsed==null) { run.missingCount++; continue }
                        val sample=parsed.count
                        val existing=counts.findByDeviceIdAndSubscriptionIdAndInformAt(deviceId,subId,inform)
                        // Keep a completed reading intact if the next NBI response is incomplete.
                        if(existing?.qualityStatus==Quality.FRESH && !parsed.complete) continue
                        sample.id=existing?.id; sample.sourceRunId=run.id
                        counts.saveAndFlush(sample)
                        val oldStations=stations.findByCountSampleId(sample.id!!).associateBy { it.stationKey to it.band }
                        for(station in parsed.stations) {
                            station.countSampleId=sample.id!!
                            station.id=oldStations[station.stationKey to station.band]?.id
                            stations.save(station)
                        }
                        if(parsed.complete) {
                            val observedKeys=parsed.stations.map { it.stationKey to it.band }.toSet()
                            stations.deleteAll(oldStations.filterKeys { it !in observedKeys }.values)
                        }
                        val status=current.findById(subId).orElse(WifiCurrent(subscriptionId=subId))
                        if(status.informAt==null || !inform.isBefore(status.informAt)) {
                            status.deviceId=deviceId; status.model=model; status.informAt=inform; status.updatedAt=now
                            status.qualityStatus=sample.qualityStatus
                            if(parsed.complete) {
                                status.countSampleId=sample.id; status.observedAt=sample.observedAt; status.associatedDeviceCount=sample.associatedDeviceCount
                            }
                            current.save(status)
                        }
                        val key="acs:$deviceId"
                        val cursor=cursors.findById(key).orElse(HealthCursor(cursorKey=key))
                        // Always re-read the latest Inform: parameter updates can finish after _lastInform advances.
                        cursor.observedAt=inform; cursor.referenceId=sample.id!!; cursor.updatedAt=now; cursors.save(cursor)
                        when(sample.qualityStatus) { Quality.UNSUPPORTED -> run.unsupportedCount++; Quality.MISSING -> run.missingCount++; else -> run.writtenCount++ }
                        run.lagSeconds=maxOf(run.lagSeconds,Duration.between(inform,now).seconds.coerceAtLeast(0))
                    }
                }
                run.completedAt=Instant.now()
            } catch (_: Exception) {
                // Never log an NBI body, URL or device tree.
                run.qualityStatus=Quality.ERROR; run.errorCount++; run.errorReason="ACS_CACHE_READ_FAILED"; run.completedAt=Instant.now()
            }
            runs.save(run)
        } } catch (_: Exception) {
            // A persistence failure rolls samples and cursors back together. Record it outside that transaction.
            tx.executeWithoutResult {
                runs.save(TelemetryRun(source="ACS",equipmentKey="genieacs",startedAt=Instant.now(),completedAt=Instant.now(),
                    qualityStatus=Quality.ERROR,errorCount=1,errorReason="ACS_TRANSACTION_ROLLED_BACK"))
            }
        }
    }
}
