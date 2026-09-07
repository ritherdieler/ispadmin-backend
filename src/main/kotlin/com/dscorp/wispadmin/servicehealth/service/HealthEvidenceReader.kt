package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.dto.*
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.events.LiveTelemetryPort
import com.dscorp.wispadmin.servicehealth.port.HealthCpePort
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagPort
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagTarget
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficSample
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

/** Only domain repositories/cache are consulted. No device I/O from a read request. */
data class HealthInputs(
    val subscriptionId: Int, val now: Instant, val identity: Map<String,String>, val sources: List<Evidence>,
    val optical: List<OpticalSample>, val flaps: List<OnuStateEvent>, val wifi: List<WifiStationSample>,
    val trafficEvents: List<TrafficEvidence>, val routerReasons: Set<String>, val targets: List<HealthNetDiagTarget>,
    val serviceStatus: String, val planSnapshot: Map<String,Any?>
)

@Service
class HealthEvidenceReader(
    private val subscriptions: SubscriptionRepository, private val identity: IdentityService,
    private val cpePort: ObjectProvider<HealthCpePort>, private val onuPort: ObjectProvider<HealthOnuPort>,
    private val optical: OpticalSampleRepository, private val states: OnuStateEventRepository,
    private val cursors: HealthCursorRepository, private val wifi: WifiCurrentRepository,
    private val stations: WifiStationSampleRepository, private val counts: WifiCountSampleRepository,
    private val trafficPort: ObjectProvider<HealthTrafficPort>,
    private val liveTelemetry: ObjectProvider<LiveTelemetryPort>,
    private val trafficEvidence: TrafficEvidenceRepository,
    private val runs: TelemetryRunRepository, private val netDiagPort: ObjectProvider<HealthNetDiagPort>,
    private val properties: ServiceHealthProperties, private val json: ObjectMapper
) {
    fun requireExists(id: Int) {
        if(!subscriptions.existsById(id)) throw NoSuchElementException("Suscripción inexistente")
    }
    @Transactional(readOnly=true)
    fun read(id: Int, now: Instant = Instant.now()): HealthInputs {
        val sub=subscriptions.findById(id).orElseThrow { NoSuchElementException("Suscripción $id no encontrada") }
        val ids=identity.snapshot(sub).toMutableMap()
        ids["lab"] = if (id in properties.labSubscriptionIds) "true" else "false"
        val from=now.minusSeconds(86400)
        val trafficIdentitySince=identity.currentLinks(id).filter { it.kind in setOf("IP","ROUTER","QUEUE","PLAN","ONU") }.maxOfOrNull { it.validFrom }
        val sources=mutableListOf<Evidence>()
        val onu=ids["ONU"]?.let { onuPort.ifAvailable?.findBySn(it) }
        val liveOnu = liveTelemetry.ifUnique?.onu(id)?.takeIf { ids["ONU"] == null || it.sn.equals(ids["ONU"], ignoreCase = true) }
        val state=onu?.id?.let { states.findTopByOnuIdOrderByObservedAtDesc(it) }?.takeIf {
            it.subscriptionId==id && it.oltId.toString()==ids["OLT"] && "${it.oltId}:${it.board}:${it.port}"==ids["PON"]
        }
        val seen=if(liveOnu!=null) liveOnu.observedAt else if(state!=null) onu?.id?.let { cursors.findById("state:$it").orElse(null)?.observedAt } else null
        val runStateValue = liveOnu?.runState ?: state?.state
        sources+=Evidence("OLT","run_state",seen,runStateValue,qualityAt(seen,now,properties.stateFreshSeconds),
            onu?.id?.toString(),onu?.externalId?.let { "/onus/configured/$it" })
        val optics=optical.listBySubscriptionInUtcWindow(id,from,now)
            .filter { sample ->
                val onuIdMatch = ids["ONU_ID"]?.let { sample.onuId.toString() == it } == true
                val snMatch = sample.onuSn.equals(ids["ONU"], ignoreCase = true)
                (onuIdMatch || snMatch) && sample.oltId.toString()==ids["OLT"] && "${sample.oltId}:${sample.board}:${sample.port}"==ids["PON"]
            }
        val lastOptical=optics.lastOrNull()
        sources+=Evidence("OLT","onu_rx_dbm",lastOptical?.observedAt,lastOptical?.onuRxDbm,
            if(lastOptical?.onuRxDbm==null) Quality.MISSING else qualityAt(lastOptical.observedAt,now,properties.opticalFreshSeconds),lastOptical?.id?.toString())
        val opticalRun=ids["OLT"]?.let { runs.findTopBySourceAndEquipmentKeyOrderByStartedAtDesc("OLT_OPTICAL",it) }
        sources+=Evidence("OLT","collector",opticalRun?.completedAt,opticalRun?.qualityStatus?.name,
            if(opticalRun?.qualityStatus==Quality.ERROR) Quality.ERROR else qualityAt(opticalRun?.completedAt,now,properties.opticalFreshSeconds),opticalRun?.id?.toString())
        val telemetry = ids["ONU"]?.let { cpePort.ifAvailable?.telemetry(it) }
        val w=wifi.findById(id).orElse(null)
        val model=telemetry?.productClass ?: w?.model
        model?.let { ids["CPE_MODEL"]=it }
        telemetry?.uniqueExternalId?.let { ids["CPE_EXTERNAL_ID"]=it }
        val liveInform=telemetry?.lastInformAt?.takeUnless { it.isAfter(now.plusSeconds(60)) }
        val lastInform=liveInform ?: w?.informAt
        sources+=Evidence("ACS","last_inform",lastInform,lastInform,qualityAt(lastInform,now,properties.periodicInformSeconds*2),ids["ACS"])
        val wifiSupported=WifiTelemetry.radios(model ?: "").isNotEmpty()
        val wifiQuality=when {
            !wifiSupported -> Quality.UNSUPPORTED
            w==null -> Quality.MISSING
            w.qualityStatus!=Quality.FRESH -> w.qualityStatus
            else -> qualityAt(w.observedAt,now,properties.wifiSampleFreshSeconds())
        }
        sources+=Evidence("ACS","associated_device_count",w?.observedAt,w?.associatedDeviceCount,wifiQuality,w?.countSampleId?.toString())
        val acsRun=runs.findTopBySourceAndEquipmentKeyOrderByStartedAtDesc("ACS","gateway-cpe")
        sources+=Evidence("ACS","collector",acsRun?.completedAt,acsRun?.qualityStatus?.name,
            if(acsRun?.qualityStatus==Quality.ERROR) Quality.ERROR else qualityAt(acsRun?.completedAt,now,300),acsRun?.id?.toString())
        val liveTraffic = liveTelemetry.ifUnique?.traffic(id)
        val t = liveTraffic?.let {
            HealthTrafficSample(
                id = null,
                hostDeviceId = it.hostDeviceId ?: ids["ROUTER"]?.toIntOrNull() ?: 0,
                collectedAt = it.collectedAt?.atZone(ZoneId.of("America/Lima"))?.toLocalDateTime(),
                sampleStatus = it.sampleStatus,
                avgMbpsDown = it.avgMbpsDown,
                avgMbpsUp = it.avgMbpsUp,
                queueId = null,
            )
        } ?: trafficPort.ifAvailable?.latestSample(id)?.takeIf { it.hostDeviceId.toString()==ids["ROUTER"] }
        val tAt=t?.collectedAt?.atZone(ZoneId.of("America/Lima"))?.toInstant()
        val tq=when(t?.sampleStatus) {
            "OK" -> if((trafficIdentitySince!=null && tAt!=null && tAt<trafficIdentitySince) || (t.avgMbpsDown==null && t.avgMbpsUp==null)) Quality.MISSING else qualityAt(tAt,now,180)
            "INVALID" -> Quality.INVALID
            "STALE" -> Quality.STALE
            else -> Quality.MISSING
        }
        sources+=Evidence("TRAFFIC","mbps",tAt,t?.let { mapOf("down" to it.avgMbpsDown,"up" to it.avgMbpsUp) },tq,t?.id?.toString(),"/bandwidth-intelligence/subscriptions/$id")
        val tr=sub.hostDevice?.id?.let { trafficPort.ifAvailable?.latestRun(it) }
        val trAt=tr?.completedAt?.atZone(ZoneId.of("America/Lima"))?.toInstant()
        sources+=Evidence("TRAFFIC","collector",trAt,tr?.status,
            if(tr?.status in setOf("FAILED","ERROR")) Quality.ERROR else qualityAt(trAt,now,180),tr?.id?.toString())
        val enabledTargets=netDiagPort.ifAvailable?.enabledTargets().orEmpty()
        val matched=enabledTargets.filter { target ->
            val config=try { json.readTree(target.monitorConfig ?: "{}") } catch (_: Exception) { json.createObjectNode() }
            val kind=config.path("kind").asText().uppercase()
            when(kind) {
                "OLT" -> target.deviceRefId.toString()==ids["OLT"]
                "PON" -> target.name==onu?.let { "PON-${it.oltName}-gpon-${it.board}/${it.port}" }
                else -> target.deviceRefId.toString()==ids["ROUTER"]
            }
        }
        val router=matched.firstOrNull { target ->
            val kind=try { json.readTree(target.monitorConfig ?: "{}").path("kind").asText().uppercase() } catch (_: Exception) { "" }
            kind !in setOf("OLT","PON") && target.deviceRefId.toString()==ids["ROUTER"]
        }
        val probe=router?.id?.let { netDiagPort.ifAvailable?.latestProbe(it) }
        val probeAt=probe?.finishedAt
        val payload=try { json.readTree(probe?.payload ?: "{}") } catch (_: Exception) { json.createObjectNode() }
        val routerQuality=if(probe!=null && probe.status!="SUCCESS") Quality.ERROR else qualityAt(probeAt,now,((router?.pollIntervalMs ?: 60000)*3)/1000)
        sources+=Evidence("NETDIAG","collector",probeAt,probe?.status,routerQuality,probe?.id?.toString())
        val cpu=payload.path("resource").path("cpuLoad").takeIf { it.isNumber }?.asInt()
        sources+=Evidence("NETDIAG","cpu_load",probeAt,cpu,if(cpu==null && routerQuality==Quality.FRESH) Quality.MISSING else routerQuality,probe?.id?.toString())
        val free=payload.path("resource").path("freeMemoryBytes").takeIf { it.isNumber }?.asLong()
        val total=payload.path("resource").path("totalMemoryBytes").takeIf { it.isNumber }?.asLong()
        sources+=Evidence("NETDIAG","memory_used_pct",probeAt,if(free!=null && total!=null && total>0) 100.0*(total-free)/total else null,
            if(free==null || total==null) Quality.UNSUPPORTED else if(total<=0 || free<0 || free>total) Quality.INVALID else routerQuality,probe?.id?.toString())
        val reasonSet=router?.id?.let { targetId ->
            listOf("OPEN","ACKNOWLEDGED","SILENCED").flatMap { netDiagPort.ifAvailable?.incidentsByTargetAndStatus(targetId,it).orEmpty() }.mapNotNull { it.reasonCode }.toSet()
        } ?: emptySet()
        val ancestors=matched.toMutableList()
        var frontier=matched
        repeat(5) {
            val next=frontier.mapNotNull { child -> enabledTargets.firstOrNull { it.id==child.parentTargetId } }
                .filter { parent -> ancestors.none { it.id==parent.id } }.distinctBy { it.id }
            ancestors+=next; frontier=next
        }
        val allowedReadings=counts.listBySubscriptionInUtcWindow(id,now.minusSeconds(21600),now)
            .mapNotNull { it.id }.toSet()
        val wifiStations=if(wifiSupported) stations.listBySubscriptionInUtcWindow(id,now.minusSeconds(21600),now)
            .filter { it.countSampleId in allowedReadings } else emptyList()
        val signalAt=wifiStations.maxOfOrNull { it.observedAt }
        sources+=Evidence("ACS","wifi_signal",signalAt,mapOf("rssi_min" to wifiStations.mapNotNull { it.rssi }.minOrNull(),"snr_min" to wifiStations.mapNotNull { it.snr }.minOrNull()),
            if(!wifiSupported) Quality.UNSUPPORTED else qualityAt(signalAt,now,properties.wifiSampleFreshSeconds()))
        return HealthInputs(id,now,ids,sources,optics,
            states.listBySubscriptionInUtcWindow(id,from,now).filter { it.onuSn.equals(ids["ONU"],true) && "${it.oltId}:${it.board}:${it.port}"==ids["PON"] },
            wifiStations,
            trafficEvidence.findBySubscriptionIdAndEventStatus(id,"OPEN").filter { trafficIdentitySince==null || it.observedAt>=trafficIdentitySince },reasonSet,ancestors,sub.serviceStatus.name,
            mapOf("id" to sub.plan?.id,"download_mbps" to sub.plan?.downloadSpeed,"upload_mbps" to sub.plan?.uploadSpeed))
    }
}
