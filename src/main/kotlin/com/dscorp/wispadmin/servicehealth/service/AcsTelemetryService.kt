package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.service.genieacs.GenieAcsClient
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.sql.Timestamp

@Service
class AcsTelemetryService(
    private val properties: ServiceHealthProperties, private val client: GenieAcsClient,
    private val subscriptions: SubscriptionRepository, private val acs: SubscriptionAcsRepository,
    private val identity: IdentityService, private val counts: WifiCountSampleRepository,
    private val stations: WifiStationSampleRepository, private val current: WifiCurrentRepository,
    private val cursors: HealthCursorRepository, private val runs: TelemetryRunRepository,
    private val profiles: ReadCapabilityProfileRepository, private val actions: RemoteActionRepository,
    private val tx: TransactionTemplate
) {
    private fun sqlTimestamp(value: Instant): Timestamp = Timestamp.from(value.truncatedTo(ChronoUnit.MICROS))

    private data class PendingGpv(val deviceId: String, val model: String, val root: JsonNode)

    @Scheduled(fixedDelayString="\${service.health.acs-interval-ms:120000}",initialDelayString="\${service.health.acs-initial-delay-ms:45000}")
    fun poll() {
        if(!properties.enabled || !properties.acsEnabled || properties.pilotSubscriptionIds.isEmpty()) return
        val pending = mutableListOf<PendingGpv>()
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
                        val key="acs:$deviceId"
                        val cursor=cursors.findById(key).orElse(HealthCursor(cursorKey=key))
                        cursor.observedAt=inform; cursor.updatedAt=now
                        if(!WifiTelemetry.shouldPersist(parsed)) {
                            if(parsed.count.qualityStatus==Quality.UNSUPPORTED) run.unsupportedCount++ else run.missingCount++
                            val gpvCursor=cursors.findById(AcsWifiRefreshPlanner.cursorKey(deviceId)).orElse(null)
                            if(AcsWifiRefreshPlanner.shouldEnqueue(parsed.count.errorReason,gpvCursor?.observedAt,now,properties.acsGpvCooldownSeconds)) {
                                pending += PendingGpv(deviceId, model, device)
                            }
                            cursors.save(cursor)
                            run.lagSeconds=maxOf(run.lagSeconds,Duration.between(inform,now).seconds.coerceAtLeast(0))
                            continue
                        }
                        val incoming=parsed.count
                        val observedAt=incoming.observedAt!!
                        counts.upsertAtomic(deviceId,subId,sqlTimestamp(incoming.informAt),sqlTimestamp(observedAt),
                            sqlTimestamp(incoming.collectedAt),incoming.associatedDeviceCount,incoming.associated2g,incoming.associated5g,
                            incoming.lanDeviceCount,incoming.qualityStatus.name,run.id,incoming.errorReason)
                        val persistedId=counts.findIdByDeviceIdAndSubscriptionIdAndObservedAtSql(deviceId,subId,sqlTimestamp(observedAt))
                            ?: counts.findByDeviceIdAndSubscriptionIdAndObservedAt(deviceId,subId,observedAt)?.id
                            ?: throw IllegalStateException("ACS_SAMPLE_ID_NOT_FOUND")
                        val sample=counts.findById(persistedId).orElseThrow { IllegalStateException("ACS_SAMPLE_ID_NOT_FOUND") }
                        val oldStations=stations.findByCountSampleId(sample.id!!).associateBy { it.stationKey to it.band }
                        for(station in parsed.stations) {
                            station.countSampleId=sample.id!!
                            station.id=oldStations[station.stationKey to station.band]?.id
                            stations.save(station)
                        }
                        val observedKeys=parsed.stations.map { it.stationKey to it.band }.toSet()
                        stations.deleteAll(oldStations.filterKeys { it !in observedKeys }.values)
                        val status=current.findById(subId).orElse(WifiCurrent(subscriptionId=subId))
                        if(WifiTelemetry.applyCurrent(status,parsed,deviceId,model,now)) {
                            status.countSampleId=sample.id
                            current.save(status)
                        }
                        cursor.referenceId=sample.id!!
                        cursors.save(cursor)
                        run.writtenCount++
                        run.lagSeconds=maxOf(run.lagSeconds,Duration.between(inform,now).seconds.coerceAtLeast(0))
                    }
                }
                run.completedAt=Instant.now()
            } catch (_: Exception) {
                run.qualityStatus=Quality.ERROR; run.errorCount++; run.errorReason="ACS_CACHE_READ_FAILED"; run.completedAt=Instant.now()
            }
            runs.save(run)
        } } catch (e: Exception) {
            tx.executeWithoutResult {
                runs.save(TelemetryRun(source="ACS",equipmentKey="genieacs",startedAt=Instant.now(),completedAt=Instant.now(),
                    qualityStatus=Quality.ERROR,errorCount=1,
                    errorReason="ACS_TRANSACTION_ROLLED_BACK_${e.javaClass.simpleName}".take(120)))
            }
        }
        enqueueStaleParameterRefresh(pending)
    }

    private fun enqueueStaleParameterRefresh(pending: List<PendingGpv>) {
        if (pending.isEmpty()) return
        val limit = properties.crConcurrency.coerceIn(1, 3)
        var inFlight = actions.findByStatus("RUNNING").count { it.action in setOf("WIFI_REFRESH", "CONFIG", "REBOOT_ACS") }
        for (item in pending) {
            if (inFlight >= limit) break
            val paths = WifiTelemetry.gpvPaths(item.root, item.model)
            if (paths.isEmpty()) continue
            val accepted = try {
                client.getParameterValues(item.deviceId, paths, connectionRequest = true).accepted
            } catch (_: Exception) {
                false
            }
            if (!accepted) continue
            inFlight++
            tx.executeWithoutResult {
                val key = AcsWifiRefreshPlanner.cursorKey(item.deviceId)
                val cursor = cursors.findById(key).orElse(HealthCursor(cursorKey = key))
                val now = Instant.now()
                cursor.observedAt = now
                cursor.updatedAt = now
                cursors.save(cursor)
            }
        }
    }
}
