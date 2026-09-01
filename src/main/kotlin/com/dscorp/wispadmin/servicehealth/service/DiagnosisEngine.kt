package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.dto.*
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DiagnosisEngine(
    private val properties: ServiceHealthProperties,
    private val cpuThreshold: Int = 85,
    private val goodOpticalDbm: Double = -25.0,
    private val minimumCoveragePct: Double = 80.0,
    private val environment: GigafiberEnvironmentProperties = GigafiberEnvironmentProperties()
) {
    @Autowired
    constructor(
        properties: ServiceHealthProperties,
        netDiagPort: ObjectProvider<HealthNetDiagPort>,
        trafficPort: ObjectProvider<HealthTrafficPort>,
        environment: GigafiberEnvironmentProperties
    ) : this(
        properties,
        netDiagPort.ifAvailable?.cpuThreshold() ?: 85,
        -25.0,
        trafficPort.ifAvailable?.minimumCoveragePct() ?: 80.0,
        environment
    )
    fun evaluate(input: HealthInputs): HealthSummary {
        val missing=input.sources.filter { it.qualityStatus!=Quality.FRESH }.map {
            MissingEvidence(it.source,it.metric,it.qualityStatus,"${it.source}/${it.metric}: ${it.qualityStatus}")
        }.toMutableList()
        if(input.identity["ONU"]==null) missing+=MissingEvidence("IDENTITY","ONU",Quality.MISSING,"Vínculo ONU inexistente o ambiguo")
        if(input.identity["ACS"]==null) missing+=MissingEvidence("IDENTITY","ACS",Quality.MISSING,"Vínculo ACS inexistente o ambiguo")
        if(input.identity["PON"]==null || input.targets.none { it.parentTargetId!=null } || input.targets.any { child -> child.parentTargetId!=null && input.targets.none { it.id==child.parentTargetId } }) missing+=MissingEvidence("IDENTITY","PON",Quality.MISSING,"Topología incompleta")
        fun source(metric: String)=input.sources.firstOrNull { it.metric==metric }
        fun fresh(metric: String)=source(metric)?.qualityStatus==Quality.FRESH
        val gpon=source("run_state")?.value?.toString()?.lowercase()
        val online=fresh("run_state") && gpon=="online"
        val offline=fresh("run_state") && gpon=="offline"
        val mbps=source("mbps")?.value as? Map<*,*>
        val trafficPresent=fresh("mbps") && mbps?.values?.any { (it as? Number)?.toDouble()?.let { v -> v>0 }==true }==true
        if(offline && trafficPresent) missing+=MissingEvidence("CORRELATION","source_timing",Quality.INVALID,"GPON offline y tráfico positivo: revisar diferencias temporales antes de atribuir un corte")
        val cpu=(source("cpu_load")?.value as? Number)?.toDouble()
        val routerHealthy=fresh("cpu_load") && cpu!=null && cpu<cpuThreshold && input.routerReasons.isEmpty()
        val opticalHealthy=fresh("onu_rx_dbm") && ((source("onu_rx_dbm")?.value as? Number)?.toDouble() ?: -999.0)>=goodOpticalDbm
        val diagnoses=mutableListOf<Diagnosis>()
        fun add(code: String, cause: String, evidence: List<Evidence>, next: String) {
            val freshSources=evidence.filter { it.qualityStatus==Quality.FRESH }.map { it.source }.distinct()
            val confidence=when { freshSources.size>=3 && missing.none { it.qualityStatus!=Quality.UNSUPPORTED } -> Confidence.HIGH
                freshSources.size>=2 -> Confidence.MEDIUM; else -> Confidence.LOW }
            diagnoses+=Diagnosis(code,cause,confidence,evidence,missing,
                listOf("Evidencia principal fresca; ${freshSources.size} fuentes independientes")+
                    missing.map { "Confianza limitada: ${it.source}/${it.metric} ${it.qualityStatus}" },next,input.identity+mapOf("subscription_id" to input.subscriptionId))
        }
        if(offline) {
            val evidence=listOfNotNull(source("run_state"),source("mbps")?.takeIf { it.qualityStatus==Quality.FRESH && !trafficPresent })
            add("GPON_DOWN","ONU sin enlace GPON; revisar acceso, energía o CPE",evidence,"Consultar causa de caída y vecinos del mismo PON")
        }
        val optics=input.optical.filter { it.qualityStatus==Quality.FRESH && it.onuRxDbm!=null }
        val flaps=input.flaps.count { it.previousState?.equals("online",true)==true && it.state.equals("offline",true) }
        if(fresh("onu_rx_dbm") && optics.size>=properties.opticalMinSamples && flaps>=properties.opticalMinFlaps) {
            val drop=optics.take(3).mapNotNull { it.onuRxDbm }.average()-optics.takeLast(3).mapNotNull { it.onuRxDbm }.average()
            if(drop>=properties.opticalDegradationDb) add("OPTICAL_DEGRADATION","Degradación óptica sostenida con flaps",
                listOfNotNull(source("onu_rx_dbm"),Evidence("OLT","rx_drop_24h_db",optics.last().observedAt,drop,Quality.FRESH),
                    Evidence("OLT","flaps_24h",input.flaps.lastOrNull()?.observedAt,flaps,Quality.FRESH)),
                "Comparar RX ONU/OLT y vecinos; inspeccionar conectores y planta externa")
        }
        val saturation=input.trafficEvents.firstOrNull { it.anomalyType=="PLAN_SATURATION" && it.coveragePct>=minimumCoveragePct && it.observedAt>=input.now.minusSeconds(900) }
        if(saturation!=null && online && opticalHealthy && routerHealthy) add("PLAN_SATURATION","Demanda sostenida cercana al plan contratado",
            listOfNotNull(Evidence("TRAFFIC","PLAN_SATURATION",saturation.observedAt,saturation.coveragePct,Quality.FRESH,saturation.eventId.toString(),"/bandwidth-intelligence/subscriptions/${input.subscriptionId}"),source("onu_rx_dbm"),source("cpu_load")),
            "Revisar persistencia, P95, plan y tendencia de dispositivos antes de recomendar cambios")
        val weakReadings=input.wifi.filter { it.qualityStatus==Quality.FRESH && it.observedAt>=input.now.minusSeconds(properties.periodicInformSeconds*2) && it.observedAt<=input.now && (it.rssi?.let { v -> v<properties.wifiRssiThreshold }==true || it.snr?.let { v -> v<properties.wifiSnrThreshold }==true) }
            .groupBy { it.countSampleId }
        val latestWeak=weakReadings.values.flatten().maxOfOrNull { it.observedAt }
        if(online && trafficPresent && weakReadings.size>=2 && latestWeak!=null && latestWeak>=input.now.minusSeconds(properties.periodicInformSeconds*2) && fresh("associated_device_count") && ((source("associated_device_count")?.value as? Number)?.toInt() ?: 0)>0) {
            add("WIFI_QUALITY","Señal Wi-Fi débil repetida con servicio activo",
                listOfNotNull(source("run_state"),source("mbps"),source("associated_device_count"),
                    Evidence("ACS","weak_signal_readings",latestWeak,weakReadings.size,Quality.FRESH)),
                "Revisar ubicación y banda del equipo; solicitar actualización Wi-Fi manual si hace falta")
        }
        if(online && trafficPresent && source("last_inform")?.qualityStatus==Quality.STALE && input.sources.any { it.source=="ACS" && it.metric=="collector" && it.qualityStatus==Quality.FRESH }) {
            add("ACS_STALE","Ruta de gestión TR-069 sin Inform reciente; Internet sigue activo",
                listOfNotNull(source("run_state"),source("mbps"),source("last_inform")),"Revisar ruta de gestión y configuración ACS; no reiniciar por esta señal sola")
        }
        if(fresh("cpu_load") && "CPU_HIGH" in input.routerReasons && cpu!=null && cpu>=cpuThreshold) {
            add("ROUTER_CAPACITY","CPU elevada corroborada por incidente de router",listOfNotNull(source("cpu_load"),source("mbps")),
                "Revisar carga sostenida, uplink, memoria y errores/drops de interfaz")
        }
        val gaps=input.sources.filter { it.metric=="collector" && it.qualityStatus in setOf(Quality.STALE,Quality.ERROR) }
        if(gaps.isNotEmpty()) add("TELEMETRY_GAP","Recolección incompleta; no confirma una caída del abonado",gaps,
            "Revisar última corrida, acceso al equipo y salud del collector antes de intervenir al cliente")
        return HealthSummary(input.subscriptionId,input.now,mapOf("gpon" to if(online) "ONLINE" else if(offline) "OFFLINE" else "UNKNOWN",
            "internet" to if(trafficPresent) "ACTIVE" else "UNKNOWN", "acs" to (source("last_inform")?.qualityStatus?.name ?: "MISSING")),
            input.sources,if(properties.correlationEnabled && collects(input)) diagnoses else emptyList(),missing,input.identity+mapOf("plan" to input.planSnapshot,"service_status" to input.serviceStatus),
            collects(input),properties.actionsEnabled && collects(input))
    }
    private fun collects(input: HealthInputs) =
        properties.collects(input.subscriptionId, input.identity["lab"] == "true", environment.normalizedTag())
}
