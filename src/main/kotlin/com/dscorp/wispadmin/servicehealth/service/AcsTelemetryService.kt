package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
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

@Service
class AcsTelemetryService(
    private val properties: ServiceHealthProperties, private val scope: ServiceHealthScope, private val client: GenieAcsClient,
    private val subscriptions: SubscriptionRepository, private val acs: SubscriptionAcsRepository,
    private val identity: IdentityService, private val counts: WifiCountSampleRepository,
    private val stations: WifiStationSampleRepository, private val current: WifiCurrentRepository,
    private val cursors: HealthCursorRepository, private val runs: TelemetryRunRepository,
    private val profiles: ReadCapabilityProfileRepository, private val actions: RemoteActionRepository,
    private val tx: TransactionTemplate
) {
    private fun sqlUtcText(value: Instant): String = HealthSqlTime.utcSqlText(value)

    private data class PendingGpv(val deviceId: String, val paths: List<String>, val cursorKey: String)

    @Scheduled(fixedDelayString="\${service.health.acs-interval-ms:120000}",initialDelayString="\${service.health.acs-initial-delay-ms:45000}")
    fun poll() {
        if(!properties.enabled || !properties.acsEnabled) return
        val collectIds=scope.collectionSubscriptionIds()
        if(collectIds.isEmpty()) return
        val pending = mutableListOf<PendingGpv>()
        try { tx.executeWithoutResult {
            if(cursors.lock("acs-watcher") == null) return@executeWithoutResult
            val now=Instant.now()
            val run=runs.save(TelemetryRun(source="ACS",equipmentKey="genieacs",startedAt=now))
            try {
                val ids=collectIds.flatMap { id ->
                    listOfNotNull(acs.findById(id).orElse(null)?.genieacsDeviceId,
                        subscriptions.findById(id).orElse(null)?.tr069DeviceId)
                }.plus(if (scope.environmentTag().isBlank()) properties.pilotAcsDeviceIds else emptyList()).distinct()
                for(batch in ids.chunked(50)) {
                    val devices=client.readDeviceCache(batch,WifiTelemetry.countProjection())
                    run.missingCount+=batch.size-devices.size
                    for(device in devices) {
                        run.readCount++
                        val deviceId=device.path("_id").asText()
                        val subId=identity.resolveAcsForCollection(deviceId)
                        if(subId==null) { run.unmappedCount++; continue }
                        if(!scope.collects(subId)) continue
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
                                pending += PendingGpv(deviceId, WifiTelemetry.gpvPaths(device, model), AcsWifiRefreshPlanner.cursorKey(deviceId))
                            }
                            cursors.save(cursor)
                            run.lagSeconds=maxOf(run.lagSeconds,Duration.between(inform,now).seconds.coerceAtLeast(0))
                            continue
                        }
                        val incoming=parsed.count
                        val observedAt=incoming.observedAt!!
                        counts.upsertAtomic(deviceId,subId,sqlUtcText(incoming.informAt),sqlUtcText(observedAt),
                            sqlUtcText(incoming.collectedAt),incoming.associatedDeviceCount,incoming.associated2g,incoming.associated5g,
                            incoming.lanDeviceCount,incoming.qualityStatus.name,run.id,incoming.errorReason)
                        val persistedId=AcsWifiSampleLookup.idAfterUpsert(
                            counts.findIdByDeviceIdAndSubscriptionIdAndObservedAtSql(deviceId,subId,sqlUtcText(observedAt)),
                            counts.findByDeviceIdAndSubscriptionIdAndObservedAt(deviceId,subId,observedAt.truncatedTo(ChronoUnit.SECONDS))?.id,
                            counts.findTopByDeviceIdAndSubscriptionIdOrderByInformAtDesc(deviceId,subId)?.id
                        )
                        val sample=counts.findById(persistedId).orElseThrow { IllegalStateException("ACS_SAMPLE_ID_NOT_FOUND") }
                        val expected=incoming.associatedDeviceCount ?: 0
                        var stationReading=parsed
                        if(expected>0 && parsed.stations.size<expected) {
                            val n2=incoming.associated2g ?: 0
                            val n5=incoming.associated5g ?: 0
                            val hosts=incoming.lanDeviceCount ?: 0
                            val detail=try {
                                client.readDeviceCache(listOf(deviceId),WifiTelemetry.stationProjection(model,n2,n5,hosts)).singleOrNull()
                            } catch (_: Exception) { null }
                            if(detail!=null) stationReading=WifiTelemetry.parse(detail,subId,if(profile.wifiCount) model else "",now,properties.stationHmacKey) ?: parsed
                        }
                        val oldStations=stations.findByCountSampleId(sample.id!!).associateBy { it.stationKey to it.band }
                        if(expected==0) {
                            stations.deleteAll(oldStations.values)
                        } else if(stationReading.stations.isNotEmpty()) {
                            for(station in stationReading.stations) {
                                station.countSampleId=sample.id!!
                                station.id=oldStations[station.stationKey to station.band]?.id
                                stations.save(station)
                            }
                            val observedKeys=stationReading.stations.map { it.stationKey to it.band }.toSet()
                            stations.deleteAll(oldStations.filterKeys { it !in observedKeys }.values)
                        }
                        val staCursor=cursors.findById(AcsWifiRefreshPlanner.stationCursorKey(deviceId)).orElse(null)
                        if(AcsWifiRefreshPlanner.shouldEnqueueStations(expected,stationReading.stations.size,staCursor?.observedAt,now,properties.acsGpvCooldownSeconds)) {
                            pending += PendingGpv(deviceId, WifiTelemetry.gpvStationPaths(model,incoming.associated2g,incoming.associated5g,incoming.lanDeviceCount),
                                AcsWifiRefreshPlanner.stationCursorKey(deviceId))
                        } else {
                            val sampleAt=stationReading.stations.maxOfOrNull { it.observedAt } ?: incoming.observedAt
                            val targetCursor=cursors.findById(AcsWifiRefreshPlanner.sampleCursorKey(deviceId)).orElse(null)
                            if(AcsWifiRefreshPlanner.shouldEnqueueTargetSample(sampleAt,targetCursor?.observedAt,now,properties.acsWifiSampleTargetSeconds,properties.acsGpvCooldownSeconds)) {
                                val paths=WifiTelemetry.gpvPaths(device,model)+if(expected>0)
                                    WifiTelemetry.gpvStationPaths(model,incoming.associated2g,incoming.associated5g,incoming.lanDeviceCount)
                                else emptyList()
                                pending += PendingGpv(deviceId,paths,AcsWifiRefreshPlanner.sampleCursorKey(deviceId))
                            }
                        }
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
            val paths = item.paths
            if (paths.isEmpty()) continue
            val accepted = try {
                client.getParameterValues(item.deviceId, paths, connectionRequest = true).accepted
            } catch (_: Exception) {
                false
            }
            if (!accepted) continue
            inFlight++
            tx.executeWithoutResult {
                val key = item.cursorKey
                val cursor = cursors.findById(key).orElse(HealthCursor(cursorKey = key))
                val now = Instant.now()
                cursor.observedAt = now
                cursor.updatedAt = now
                cursors.save(cursor)
            }
        }
    }
}
