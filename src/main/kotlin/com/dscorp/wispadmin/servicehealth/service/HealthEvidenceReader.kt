package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.dto.*
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.traffic.repository.*
import com.dscorp.wispadmin.netdiag.domain.repository.*
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Only domain repositories/cache are consulted. No device I/O from a read request. */
data class HealthInputs(
    val subscriptionId: Int, val now: Instant, val identity: Map<String,String>, val sources: List<Evidence>,
    val optical: List<OpticalSample>, val flaps: List<OnuStateEvent>, val wifi: List<WifiStationSample>,
    val trafficEvents: List<TrafficEvidence>, val routerReasons: Set<String>, val targets: List<NetDiagTarget>,
    val serviceStatus: String, val planSnapshot: Map<String,Any?>
)

@Service
class HealthEvidenceReader(
    private val subscriptions: SubscriptionRepository, private val identity: IdentityService,
    private val acs: SubscriptionAcsRepository, private val onus: OltMgrOnuRepository,
    private val optical: OpticalSampleRepository, private val states: OnuStateEventRepository,
    private val cursors: HealthCursorRepository, private val wifi: WifiCurrentRepository,
    private val stations: WifiStationSampleRepository, private val counts: WifiCountSampleRepository, private val traffic: SubscriptionTrafficSampleRepository,
    private val trafficRuns: TrafficSourceRunRepository, private val trafficEvidence: TrafficEvidenceRepository,
    private val runs: TelemetryRunRepository, private val targets: NetDiagTargetRepository,
    private val probes: NetDiagProbeRunRepository, private val incidents: NetDiagIncidentRepository,
    private val properties: ServiceHealthProperties, private val json: ObjectMapper
) {
    fun requireExists(id: Int) {
        if(!subscriptions.existsById(id)) throw NoSuchElementException("Suscripción inexistente")
    }
    @Transactional(readOnly=true)
    fun read(id: Int, now: Instant = Instant.now()): HealthInputs {
        val sub=subscriptions.findById(id).orElseThrow { NoSuchElementException("Suscripción $id no encontrada") }
        val ids=identity.snapshot(sub).toMutableMap()
        val from=now.minusSeconds(86400)
        val trafficIdentitySince=identity.currentLinks(id).filter { it.kind in setOf("IP","ROUTER","QUEUE","PLAN","ONU") }.maxOfOrNull { it.validFrom }
        val sources=mutableListOf<Evidence>()
        val onu=ids["ONU"]?.let { onus.findBySnIgnoreCaseAndDeletedAtIsNull(it).orElse(null) }
        val state=onu?.id?.let { states.findTopByOnuIdOrderByObservedAtDesc(it) }?.takeIf {
            it.subscriptionId==id && it.oltId.toString()==ids["OLT"] && "${it.oltId}:${it.board}:${it.port}"==ids["PON"]
        }
        val seen=if(state!=null) onu?.id?.let { cursors.findById("state:$it").orElse(null)?.observedAt } else null
        // Legacy polledAt conflates optical and inventory timestamps and is deliberately not used for state freshness.
        sources+=Evidence("OLT","run_state",seen,state?.state,qualityAt(seen,now,properties.stateFreshSeconds),
            onu?.id?.toString(),onu?.externalId?.let { "/onus/configured/$it" })
        val optics=optical.findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtAsc(id,from,now)
            .filter { it.onuSn.equals(ids["ONU"],ignoreCase=true) && it.oltId.toString()==ids["OLT"] && "${it.oltId}:${it.board}:${it.port}"==ids["PON"] }
        val lastOptical=optics.lastOrNull()
        sources+=Evidence("OLT","onu_rx_dbm",lastOptical?.observedAt,lastOptical?.onuRxDbm,
            if(lastOptical?.onuRxDbm==null) Quality.MISSING else qualityAt(lastOptical.observedAt,now,properties.opticalFreshSeconds),lastOptical?.id?.toString())
        val opticalRun=ids["OLT"]?.let { runs.findTopBySourceAndEquipmentKeyOrderByStartedAtDesc("OLT_OPTICAL",it) }
        sources+=Evidence("OLT","collector",opticalRun?.completedAt,opticalRun?.qualityStatus?.name,
            if(opticalRun?.qualityStatus==Quality.ERROR) Quality.ERROR else qualityAt(opticalRun?.completedAt,now,properties.opticalFreshSeconds),opticalRun?.id?.toString())
        val a=acs.findById(id).orElse(null)?.takeIf { it.genieacsDeviceId==ids["ACS"] }
        val w=wifi.findById(id).orElse(null)?.takeIf { it.deviceId==ids["ACS"] }
        val model=a?.productClass ?: w?.model
        model?.let { ids["CPE_MODEL"]=it }
        val lastInform=a?.lastInformAt?.toInstant(ZoneOffset.UTC) ?: w?.informAt
        sources+=Evidence("ACS","last_inform",lastInform,lastInform,qualityAt(lastInform,now,properties.periodicInformSeconds*2),ids["ACS"])
        val wifiSupported=WifiTelemetry.radios(model ?: "").isNotEmpty()
        val wifiQuality=when {
            !wifiSupported -> Quality.UNSUPPORTED
            w==null -> Quality.MISSING
            w.qualityStatus!=Quality.FRESH -> w.qualityStatus
            else -> qualityAt(w.observedAt,now,properties.periodicInformSeconds*2)
        }
        sources+=Evidence("ACS","associated_device_count",w?.observedAt,w?.associatedDeviceCount,wifiQuality,w?.countSampleId?.toString())
        val acsRun=runs.findTopBySourceAndEquipmentKeyOrderByStartedAtDesc("ACS","genieacs")
        sources+=Evidence("ACS","collector",acsRun?.completedAt,acsRun?.qualityStatus?.name,
            if(acsRun?.qualityStatus==Quality.ERROR) Quality.ERROR else qualityAt(acsRun?.completedAt,now,300),acsRun?.id?.toString())
        val t=traffic.findTopBySubscriptionIdOrderByBucketStartDesc(id)?.takeIf { it.hostDeviceId.toString()==ids["ROUTER"] }
        val tAt=t?.collectedAt?.atZone(ZoneId.of("America/Lima"))?.toInstant()
        val tq=when(t?.sampleStatus?.name) {
            "OK" -> if((trafficIdentitySince!=null && tAt!=null && tAt<trafficIdentitySince) || (t.avgMbpsDown==null && t.avgMbpsUp==null)) Quality.MISSING else qualityAt(tAt,now,180)
            "INVALID" -> Quality.INVALID
            "STALE" -> Quality.STALE
            else -> Quality.MISSING
        }
        sources+=Evidence("TRAFFIC","mbps",tAt,t?.let { mapOf("down" to it.avgMbpsDown,"up" to it.avgMbpsUp) },tq,t?.id?.toString(),"/bandwidth-intelligence/subscriptions/$id")
        val tr=sub.hostDevice?.id?.let { trafficRuns.findTopByHostDeviceIdOrderByStartedAtDesc(it) }
        val trAt=tr?.completedAt?.atZone(ZoneId.of("America/Lima"))?.toInstant()
        sources+=Evidence("TRAFFIC","collector",trAt,tr?.status?.name,
            if(tr?.status?.name in setOf("FAILED","ERROR")) Quality.ERROR else qualityAt(trAt,now,180),tr?.id?.toString())
        val enabledTargets=targets.findByEnabledTrue()
        val matched=enabledTargets.filter { target ->
            val config=try { json.readTree(target.monitorConfig ?: "{}") } catch (_: Exception) { json.createObjectNode() }
            val kind=config.path("kind").asText().uppercase()
            when(kind) {
                "OLT" -> target.deviceRefId.toString()==ids["OLT"]
                "PON" -> target.name==onu?.let { "PON-${it.olt.name}-gpon-${it.board}/${it.port}" }
                else -> target.deviceRefId.toString()==ids["ROUTER"]
            }
        }
        val router=matched.firstOrNull { target ->
            val kind=try { json.readTree(target.monitorConfig ?: "{}").path("kind").asText().uppercase() } catch (_: Exception) { "" }
            kind !in setOf("OLT","PON") && target.deviceRefId.toString()==ids["ROUTER"]
        }
        val probe=router?.id?.let { probes.findTopByTargetIdOrderByStartedAtDesc(it).orElse(null) }
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
            listOf("OPEN","ACKNOWLEDGED","SILENCED").flatMap { incidents.findByTarget_IdAndStatus(targetId,it) }.mapNotNull { it.reasonCode }.toSet()
        } ?: emptySet()
        val ancestors=matched.toMutableList()
        var frontier=matched
        repeat(5) {
            val next=frontier.mapNotNull { child -> enabledTargets.firstOrNull { it.id==child.parentTargetId } }
                .filter { parent -> ancestors.none { it.id==parent.id } }.distinctBy { it.id }
            ancestors+=next; frontier=next
        }
        val allowedReadings=counts.findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtAsc(id,now.minusSeconds(21600),now)
            .filter { it.deviceId==ids["ACS"] }.mapNotNull { it.id }.toSet()
        val wifiStations=if(wifiSupported && ids["ACS"]!=null) stations.findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtAsc(id,now.minusSeconds(21600),now)
            .filter { it.countSampleId in allowedReadings } else emptyList()
        val signalAt=wifiStations.maxOfOrNull { it.observedAt }
        sources+=Evidence("ACS","wifi_signal",signalAt,mapOf("rssi_min" to wifiStations.mapNotNull { it.rssi }.minOrNull(),"snr_min" to wifiStations.mapNotNull { it.snr }.minOrNull()),
            if(!wifiSupported) Quality.UNSUPPORTED else qualityAt(signalAt,now,properties.periodicInformSeconds*2))
        return HealthInputs(id,now,ids,sources,optics,
            states.findBySubscriptionIdAndObservedAtBetweenOrderByObservedAtAsc(id,from,now).filter { it.onuSn.equals(ids["ONU"],true) && "${it.oltId}:${it.board}:${it.port}"==ids["PON"] },
            wifiStations,
            trafficEvidence.findBySubscriptionIdAndEventStatus(id,"OPEN").filter { trafficIdentitySince==null || it.observedAt>=trafficIdentitySince },reasonSet,ancestors,sub.serviceStatus.name,
            mapOf("id" to sub.plan?.id,"download_mbps" to sub.plan?.downloadSpeed,"upload_mbps" to sub.plan?.uploadSpeed))
    }
}
