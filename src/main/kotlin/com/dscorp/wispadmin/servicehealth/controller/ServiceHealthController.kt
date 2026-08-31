package com.dscorp.wispadmin.servicehealth.controller

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.dto.*
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.servicehealth.service.*
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069ModelProfiles
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.bind.annotation.*
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import javax.servlet.http.HttpServletRequest

@RestController
class ServiceHealthController(private val access: HealthAccess,private val reader: HealthEvidenceReader,
    private val engine: DiagnosisEngine,private val properties: ServiceHealthProperties,
    private val acs: SubscriptionAcsRepository,private val optical: OpticalSampleRepository,
    private val counts: WifiCountSampleRepository,private val stations: WifiStationSampleRepository,
    private val events: HealthEventRepository,private val actions: RemoteActionRepository,
    private val remote: RemoteActionService,private val identity: IdentityService,
    private val conflicts: IdentityConflictRepository,private val affected: IncidentSubscriptionRepository,
    private val onus: OltMgrOnuRepository,private val json: ObjectMapper) {

    @GetMapping("/subscription/{id}/cpe-status")
    fun cpe(@PathVariable id: Int,request: HttpServletRequest): CpeStatus {
        val actor=access.require(request)
        val summary=engine.evaluate(reader.read(id))
        val snap=acs.findById(id).orElse(null)
        val rx=summary.sources.firstOrNull { it.metric=="onu_rx_dbm" }
        val state=summary.sources.first { it.metric=="run_state" }
        val inform=summary.sources.first { it.metric=="last_inform" }
        val model=summary.identity["CPE_MODEL"] as? String
        val profile=Tr069ModelProfiles.resolve(null,model)
        val writable=summary.actionsEnabled && properties.configEnabled && profile!=null && inform.qualityStatus==Quality.FRESH && summary.identity["ACS"]!=null
        return CpeStatus(summary.states["gpon"]=="ONLINE",rx?.value?.toString(),inform.observedAt,summary.identity["ONU"] as? String,
            CpeGponStatus(summary.states["gpon"]!!,rx?.value?.toString(),null,state.qualityStatus),
            CpeAcsStatus(if(inform.qualityStatus==Quality.FRESH) "SYNCED" else if(inform.qualityStatus==Quality.STALE) "STALE" else "UNKNOWN",
                inform.observedAt,inform.qualityStatus==Quality.FRESH,inform.qualityStatus),
            CpeCapabilities(canWriteWanViaTr069=writable && actor.role=="ADMIN",canWriteWifiViaTr069=writable,
                vendor=snap?.manufacturer,model=model),summary.actionsEnabled)
    }

    @GetMapping("/subscription/{id}/service-health")
    fun summary(@PathVariable id: Int,request: HttpServletRequest): HealthSummary {
        access.require(request)
        val summary=engine.evaluate(reader.read(id))
        val open=events.findBySubscriptionIdAndEventStatus(id,"OPEN")
        return summary.copy(diagnoses=summary.diagnoses.map { d ->
            d.copy(suppressingIncidentId=open.firstOrNull { it.diagnosisCode==d.diagnosisCode }?.suppressingIncidentId)
        })
    }

    @GetMapping("/subscription/{id}/service-health/series")
    fun series(@PathVariable id: Int,@RequestParam(required=false) from: String?,@RequestParam(required=false) to: String?,request: HttpServletRequest): Map<String,Any> {
        access.require(request); reader.requireExists(id)
        val window=window(from,to)
        return seriesData(id,window.first,window.second)
    }
    private fun seriesData(id: Int,from: Instant,to: Instant): Map<String,Any> = mapOf(
        "optical" to optical.listBySubscriptionInUtcWindow(id,from,to).map { s -> mapOf(
            "id" to s.id,"observed_at" to s.observedAt,"collected_at" to s.collectedAt,"onu_id" to s.onuId,
            "onu_sn" to s.onuSn,"olt_id" to s.oltId,"board" to s.board,"port" to s.port,
            "onu_rx_dbm" to s.onuRxDbm,"onu_tx_dbm" to s.onuTxDbm,"olt_rx_dbm" to s.oltRxDbm,
            "temperature_c" to s.temperatureC,"distance_m" to s.distanceM,"bias_ma" to s.biasMa,"voltage_v" to s.voltageV,"quality_status" to s.qualityStatus) },
        "wifi_counts" to counts.listBySubscriptionInUtcWindow(id,from,to).map { s -> mapOf(
            "id" to s.id,"observed_at" to s.observedAt,"associated_device_count" to s.associatedDeviceCount,
            "associated_2g" to s.associated2g,"associated_5g" to s.associated5g,"lan_device_count" to s.lanDeviceCount,"quality_status" to s.qualityStatus) },
        "wifi_signal" to stations.listBySubscriptionInUtcWindow(id,from,to).map { s -> mapOf(
            "reading_id" to s.countSampleId,"observed_at" to s.observedAt,"band" to s.band,"rssi" to s.rssi,"snr" to s.snr,"quality_status" to s.qualityStatus) }
    )

    @GetMapping("/subscription/{id}/service-health/timeline")
    fun timeline(@PathVariable id: Int,@RequestParam(required=false) from: String?,@RequestParam(required=false) to: String?,
                 @RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="50") size: Int,request: HttpServletRequest): Map<String,Any> {
        access.require(request); reader.requireExists(id)
        val w=window(from,to); val pagination=page(page,size)
        val result=events.pageBySubscriptionInUtcWindow(id,w.first,w.second,pagination)
        return mapOf("items" to result.content.map { e -> mapOf("id" to e.id,"observed_at" to e.observedAt,
            "ended_at" to e.endedAt,"status" to e.eventStatus,"diagnosis" to json.readTree(e.diagnosisJson),
            "identity" to json.readTree(e.identitySnapshotJson),"suppressing_incident_id" to e.suppressingIncidentId) },
            "page" to result.number,"total_elements" to result.totalElements,"total_pages" to result.totalPages,
            "actions" to actions.listBySubscriptionCreatedInUtcWindow(id,w.first,w.second).take(100).map { a ->
                mapOf("id" to a.id,"observed_at" to a.createdAt,"action" to a.action,"status" to a.status,"actor_id" to a.actorId) })
    }

    @GetMapping("/onu/configured/{externalId}/optical-series")
    @Transactional(readOnly=true)
    fun onuSeries(@PathVariable externalId: String,@RequestParam(required=false) from: String?,@RequestParam(required=false) to: String?,request: HttpServletRequest): Map<String,Any> {
        access.require(request)
        val onu=onus.findByExternalIdAndDeletedAtIsNull(externalId).orElseThrow { NoSuchElementException("ONU inexistente") }
        val id=identity.resolveOnu(onu.sn)
        val w=window(from,to)
        val data=optical.listByOnuInUtcWindow(onu.id!!,w.first,w.second).map { sample ->
            mapOf("id" to sample.id,"observed_at" to sample.observedAt,"onu_id" to sample.onuId,"onu_sn" to sample.onuSn,
                "subscription_id" to sample.subscriptionId,"onu_rx_dbm" to sample.onuRxDbm,"onu_tx_dbm" to sample.onuTxDbm,
                "olt_rx_dbm" to sample.oltRxDbm,"quality_status" to sample.qualityStatus)
        }
        return if(id==null) mapOf("optical" to data) else mapOf("subscription_id" to id,"optical" to data)
    }

    @PostMapping("/subscription/{id}/service-health/reboot")
    fun reboot(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<ActionResult> {
        val actor = access.require(request)
        val result = remote.reboot(id, actor, access.confirmation(request))
        return ResponseEntity.status(if (result.status in setOf("PENDING", "RUNNING")) 202 else 200).body(result)
    }
    @PostMapping("/subscription/{id}/acs/wifi-refresh")
    fun refresh(@PathVariable id: Int,request: HttpServletRequest): ResponseEntity<ActionResult> {
        val actor=access.require(request); val result=remote.refresh(id,actor,access.confirmation(request))
        return ResponseEntity.status(if(result.status in setOf("PENDING","RUNNING")) 202 else 200).body(result)
    }
    @PutMapping("/subscription/{id}/cpe-config")
    fun configure(@PathVariable id: Int,@RequestBody config: CpeConfiguration,request: HttpServletRequest): ResponseEntity<ActionResult> {
        val actor=access.require(request,config.network!=null)
        val result=remote.configure(id,actor,access.confirmation(request),config)
        return ResponseEntity.status(if(result.status in setOf("PENDING","RUNNING")) 202 else 200).body(result)
    }
    @GetMapping("/subscription/{id}/service-health/actions/{actionId}")
    fun action(@PathVariable id: Int,@PathVariable actionId: Long,request: HttpServletRequest): ActionResult {
        access.require(request)
        val a=actions.findById(actionId).orElseThrow { NoSuchElementException("Acción inexistente") }
        if(a.subscriptionId!=id) throw NoSuchElementException("Acción inexistente")
        return remote.result(a)
    }
    @GetMapping("/api/netdiag/incidents/{id}/affected-subscriptions")
    fun affected(@PathVariable id: Long,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="50") size: Int,request: HttpServletRequest): Map<String,Any> {
        access.require(request)
        val result=affected.findByIncidentId(id,page(page,size))
        return mapOf("items" to result.content.map { row -> mapOf("subscription_id" to row.subscriptionId,"state" to row.state,
            "first_seen_at" to row.firstSeenAt,"recovered_at" to row.recoveredAt,"scope" to json.readTree(row.scopeJson),
            "diagnosis" to row.healthEventId?.let { eventId -> events.findById(eventId).orElse(null)?.diagnosisJson?.let { json.readTree(it) } }) },
            "total_elements" to result.totalElements,"page" to result.number,"total_pages" to result.totalPages)
    }
    @GetMapping("/service-health/identity-conflicts")
    fun conflicts(@RequestParam(defaultValue="0") page: Int,request: HttpServletRequest): Any {
        access.require(request,true); return conflicts.findByStatusOrderByCreatedAtDesc("OPEN",page(page,50))
    }
    data class ResolveConflict(val subscriptionId: Int = 0,val reason: String = "")
    @PostMapping("/service-health/identity-conflicts/{id}/resolve")
    fun resolve(@PathVariable id: Long,@RequestBody body: ResolveConflict,request: HttpServletRequest): Any {
        val actor=access.require(request,true); return identity.resolveConflict(id,body.subscriptionId,actor.id,body.reason)
    }
    private fun page(page: Int,size: Int): PageRequest {
        require(page>=0 && size in 1..100) { "Paginación inválida" }; return PageRequest.of(page,size)
    }
    private fun window(from: String?,to: String?): Pair<Instant,Instant> {
        val end=to?.let { Instant.parse(it) } ?: Instant.now()
        val start=from?.let { Instant.parse(it) } ?: end.minusSeconds(86400)
        require(start<end && end.epochSecond-start.epochSecond<=90*86400) { "Ventana máxima: 90 días" }
        return start to end
    }
}

@RestControllerAdvice(assignableTypes=[ServiceHealthController::class])
class HealthErrors {
    @ExceptionHandler(NoSuchElementException::class)
    fun missing()=ResponseEntity.status(404).body(mapOf("error" to "not_found"))
    @ExceptionHandler(IllegalArgumentException::class,java.time.format.DateTimeParseException::class)
    fun invalid(ex: Exception)=ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "Solicitud inválida")))
    @ExceptionHandler(ResponseStatusException::class)
    fun denied(ex: ResponseStatusException)=ResponseEntity.status(ex.status).body(mapOf("error" to (ex.reason ?: "Acceso denegado")))
}
