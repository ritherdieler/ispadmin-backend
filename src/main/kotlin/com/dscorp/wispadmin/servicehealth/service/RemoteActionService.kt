package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.controller.HealthActor
import com.dscorp.wispadmin.servicehealth.domain.RemoteAction
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.service.genieacs.*
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class WifiConfiguration(val ssid: String = "", val password: String = "") {
    override fun toString() = "WifiConfiguration([redacted])"
}
data class NetworkConfiguration(val ipAddress: String?=null,val subnetMask: String?=null,val gateway: String?=null,
                                val dnsPrimary: String?=null,val dnsSecondary: String?=null,val vlanId: Int?=null)
data class CpeConfiguration(val network: NetworkConfiguration?=null,val wifi: WifiConfiguration?=null)
data class ActionResult(val actionId: Long,val subscriptionId: Int,val status: String,val message: String,
    val appliedNetwork: Boolean=false,val appliedWifi: Boolean=false,val networkStatus: String?=null,val wifiStatus: String?=null,
    val networkChannel: String?=null,val warnings: List<String> = emptyList())

@Service
class RemoteActionService(private val properties: ServiceHealthProperties,private val actions: RemoteActionRepository,
    private val cursors: HealthCursorRepository,private val subscriptions: SubscriptionRepository,
    private val acs: SubscriptionAcsRepository,private val wifi: WifiCurrentRepository,private val identity: IdentityService,
    private val client: GenieAcsClient,private val genie: GenieAcsProperties,private val tx: TransactionTemplate,
    private val json: ObjectMapper,
    private val subscriptionService: SubscriptionService? = null) {
    fun digest(value: String): String {
        require(properties.stationHmacKey.toByteArray().size>=32) { "HMAC_KEY_NOT_CONFIGURED" }
        val mac=Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(properties.stationHmacKey.toByteArray(),"HmacSHA256"))
        return mac.doFinal(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun reserve(id: Int,actor: HealthActor,key: String,action: String,payloadDigest: String,needsCr: Boolean): Pair<RemoteAction,Boolean> {
        if(!properties.actionsEnabled || !properties.collects(id)) throw ResponseStatusException(HttpStatus.CONFLICT,"Acciones del piloto deshabilitadas")
        return tx.execute {
            cursors.lock("actions") ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Coordinador no inicializado")
            val existing=actions.findByActorIdAndRequestKey(actor.id,key)
            if(existing!=null) {
                if(existing.subscriptionId!=id || existing.action!=action || existing.requestDigest!=payloadDigest) throw ResponseStatusException(HttpStatus.CONFLICT,"Clave reutilizada con otra operación")
                return@execute existing to false
            }
            val sub=subscriptions.findById(id).orElseThrow { NoSuchElementException("Suscripción inexistente") }
            val sn=sub.fiberOnu?.sn ?: throw ResponseStatusException(HttpStatus.CONFLICT,"ONU sin identidad")
            if(identity.resolveOnu(sn)!=id) throw ResponseStatusException(HttpStatus.CONFLICT,"Identidad ONU ambigua")
            val deviceKey="ONU:${sn.uppercase()}"
            val acsDevice=(acs.findById(id).orElse(null)?.genieacsDeviceId ?: sub.tr069DeviceId)?.takeIf { identity.resolveAcs(it)==id }
            if(needsCr && acsDevice==null) throw ResponseStatusException(HttpStatus.CONFLICT,"Identidad ACS no resuelta")
            val now=Instant.now()
            val previous=actions.findTopByDeviceKeyAndActionInOrderByCreatedAtDesc(deviceKey,
                if(action=="WIFI_REFRESH") listOf("WIFI_REFRESH","CONFIG","REBOOT_ACS") else listOf("CONFIG","REBOOT_ONU","REBOOT_ACS","WIFI_REFRESH"))
            if(previous!=null && previous.createdAt>now.minusSeconds(properties.actionCooldownSeconds)) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Espere diez minutos entre acciones sobre el equipo")
            if(needsCr && actions.findByStatus("RUNNING").count { it.action in setOf("WIFI_REFRESH","CONFIG","REBOOT_ACS") }>=properties.crConcurrency.coerceIn(1,3))
                throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Límite de conexiones ACS concurrentes")
            actions.saveAndFlush(RemoteAction(acsDeviceId=acsDevice,subscriptionId=id,actorId=actor.id,requestKey=key,deviceKey=deviceKey,action=action,
                status="RUNNING",createdAt=now,requestDigest=payloadDigest)) to true
        }!!
    }
    fun result(a: RemoteAction)=ActionResult(a.id!!,a.subscriptionId,a.status,
        when(a.status) { "CONFIRMED" -> "Lectura posterior confirma la operación"; "FAILED" -> "La operación falló; revise la evidencia";
            "UNVERIFIED" -> "Operación sin confirmación; no se reintentará automáticamente"; else -> "Solicitud pendiente; aceptación no significa aplicación" },
        a.networkStatus=="CONFIRMED",a.wifiStatus=="CONFIRMED",a.networkStatus,a.wifiStatus,a.networkChannel)

    fun finish(id: Long,status: String,taskId: String?=null,error: String?=null) = tx.execute {
        val a=actions.findById(id).orElseThrow { NoSuchElementException() }
        a.status=status; if(taskId!=null) a.taskId=taskId; a.errorReason=error;
        if(status in setOf("FAILED","UNVERIFIED")) {
            if(a.networkStatus=="PENDING") a.networkStatus=status
            if(a.wifiStatus=="PENDING") a.wifiStatus=status
        }; a.completedAt=if(status in setOf("CONFIRMED","FAILED","UNVERIFIED")) Instant.now() else null
        actions.save(a)
    }!!

    private fun replay(id: Int,actor: HealthActor,key: String,action: String,digest: String): ActionResult? {
        val existing=actions.findByActorIdAndRequestKey(actor.id,key) ?: return null
        if(existing.subscriptionId!=id || existing.action!=action || existing.requestDigest!=digest)
            throw ResponseStatusException(HttpStatus.CONFLICT,"Clave reutilizada con otra operación")
        return result(existing)
    }
    fun reboot(id: Int, actor: HealthActor, key: String): ActionResult {
        replay(id, actor, key, "REBOOT_ONU", digest("REBOOT_ONU:$id"))?.let { return it }
        val (action, created) = reserve(id, actor, key, "REBOOT_ONU", digest("REBOOT_ONU:$id"), false)
        if (!created) return result(action)
        return try {
            requireNotNull(subscriptionService) { "Reinicio no disponible" }.rebootFiberOnu(id)
            result(finish(action.id!!, "PENDING"))
        } catch (_: Exception) {
            result(finish(action.id!!, "UNVERIFIED", error = "OLT_REQUEST_UNCONFIRMED"))
        }
    }

    fun refresh(id: Int,actor: HealthActor,key: String): ActionResult {
        replay(id,actor,key,"WIFI_REFRESH",digest("WIFI_REFRESH:$id"))?.let { return it }
        val device=deviceForAction(id)
        if(WifiTelemetry.radios(device.second).isEmpty()) throw ResponseStatusException(HttpStatus.CONFLICT,"Telemetría Wi-Fi no soportada")
        val now=Instant.now()
        val last=wifi.findById(id).orElse(null)
        if(last?.observedAt?.isAfter(now.minusSeconds(900))==true) throw ResponseStatusException(HttpStatus.CONFLICT,"La muestra tiene menos de quince minutos")
        val (action,created)=reserve(id,actor,key,"WIFI_REFRESH",digest("WIFI_REFRESH:$id"),true)
        if(!created) return result(action)
        if(action.acsDeviceId!=device.first) return result(finish(action.id!!,"FAILED",error="IDENTITY_CHANGED"))
        return try {
            val root=client.readDeviceCache(listOf(device.first),WifiTelemetry.projection()).singleOrNull()
                ?: throw IllegalStateException("CACHE_MISSING")
            val paths=WifiTelemetry.gpvPaths(root,device.second)
            require(paths.isNotEmpty()) { "UNSUPPORTED" }
            val response=client.getParameterValues(device.first,paths,connectionRequest=true)
            result(finish(action.id!!,if(response.accepted) "PENDING" else "FAILED",response.taskId,if(response.accepted) null else "ACS_REJECTED"))
        } catch (_: Exception) { result(finish(action.id!!,"UNVERIFIED",error="ACS_REQUEST_UNCONFIRMED")) }
    }

    fun configure(id: Int,actor: HealthActor,key: String,request: CpeConfiguration): ActionResult {
        replay(id,actor,key,"CONFIG",digest(json.writeValueAsString(request)))?.let { return it }
        if(!properties.configEnabled) throw ResponseStatusException(HttpStatus.CONFLICT,"Configuración deshabilitada")
        if(request.network!=null && actor.role!="ADMIN") throw ResponseStatusException(HttpStatus.FORBIDDEN,"WAN requiere ADMIN")
        require(request.network!=null || request.wifi!=null) { "Configuración vacía" }
        val device=deviceForAction(id)
        val profile=Tr069ModelProfiles.resolve(null,device.second)?.forClientInternetWan(genie.clientWanIndex)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT,"Modelo sin perfil de escritura validado")
        val values=mutableListOf<Tr069ParameterValue>()
        request.wifi?.let {
            require(it.ssid.toByteArray().size in 1..32 && it.ssid.none { c -> c.isISOControl() }) { "SSID inválido" }
            require(it.password.length in 8..63 && it.password.all { c -> c.code in 32..126 }) { "Contraseña Wi-Fi inválida" }
            values+=profile.buildWifiParameterValues(it.ssid,it.password,it.ssid,it.password)
        }
        request.network?.let {
            fun ipv4(value: String?)=value!=null && value.split('.').size==4 && value.split('.').all { octet -> octet.matches(Regex("[0-9]{1,3}")) && octet.toInt() in 0..255 }
            require(ipv4(it.ipAddress) && ipv4(it.subnetMask) && ipv4(it.gateway) && ipv4(it.dnsPrimary) && (it.dnsSecondary==null || ipv4(it.dnsSecondary))) { "Se requiere WAN IPv4 completa para TR-069" }
            val mask=it.subnetMask!!.split('.').fold(0L) { total, octet -> (total shl 8) or octet.toLong() }
            val inverse=mask xor 0xffffffffL
            require(mask!=0L && (inverse and (inverse+1))==0L) { "Máscara de subred no contigua" }
            require(it.vlanId in 1..4094) { "VLAN inválida" }
            val subscription=subscriptions.findById(id).orElseThrow { NoSuchElementException("Suscripción inexistente") }
            require(it.ipAddress==subscription.ip && it.vlanId.toString()==subscription.vlan) { "Cambie primero la asignación IP/VLAN mediante el flujo de provisión" }
            values+=profile.buildWanParameterValues(it.ipAddress!!,it.subnetMask!!,it.gateway!!,listOfNotNull(it.dnsPrimary,it.dnsSecondary).joinToString(","),it.vlanId!!)
        }
        require(values.isNotEmpty()) { "Operación no soportada" }
        val (action,created)=reserve(id,actor,key,"CONFIG",digest(json.writeValueAsString(request)),true)
        if(!created) return result(action)
        if(action.acsDeviceId!=device.first) return result(finish(action.id!!,"FAILED",error="IDENTITY_CHANGED"))
        tx.executeWithoutResult {
            val a=actions.findById(action.id!!).get()
            a.networkStatus=if(request.network!=null) "PENDING" else null; a.wifiStatus=if(request.wifi!=null) "PENDING" else null
            a.networkChannel=if(request.network!=null) "TR069" else null
            // Persist expected HMACs; the original credentials exist only for this request.
            a.confirmationJson=json.writeValueAsString(values.associate { it.path to digest(it.value) })
            actions.save(a)
        }
        return try {
            val response=client.setParameterValuesPrivate(device.first,values,connectionRequest=true)
            result(finish(action.id!!,if(response.accepted) "PENDING" else "FAILED",response.taskId,if(response.accepted) null else "ACS_REJECTED"))
        } catch (_: Exception) { result(finish(action.id!!,"UNVERIFIED",error="ACS_REQUEST_UNCONFIRMED")) }
    }
    private fun deviceForAction(id: Int): Pair<String,String> {
        val s=acs.findById(id).orElse(null)
        val w=wifi.findById(id).orElse(null)
        val deviceId=s?.genieacsDeviceId ?: subscriptions.findById(id).orElse(null)?.tr069DeviceId
            ?: throw ResponseStatusException(HttpStatus.CONFLICT,"Sin deviceId")
        if(identity.resolveAcs(deviceId)!=id) throw ResponseStatusException(HttpStatus.CONFLICT,"Identidad ACS ambigua")
        val inform=s?.lastInformAt?.toInstant(ZoneOffset.UTC) ?: w?.informAt
        if(inform==null || inform<Instant.now().minusSeconds(properties.periodicInformSeconds*2) || inform>Instant.now().plusSeconds(60)) throw ResponseStatusException(HttpStatus.CONFLICT,"ACS_STALE: no se envía Connection Request")
        return deviceId to (s?.productClass ?: w?.model ?: "")
    }
    @Scheduled(fixedDelayString="\${service.health.action-check-interval-ms:120000}",initialDelayString="\${service.health.action-check-initial-delay-ms:120000}")
    fun confirmPending() {
        if(!properties.enabled || !properties.actionsEnabled) return
        for(a in actions.findByStatus("PENDING")) {
            if(!properties.collects(a.subscriptionId)) continue
            if(a.createdAt<Instant.now().minusSeconds(21600)) { finish(a.id!!,"UNVERIFIED",error="CONFIRMATION_TIMEOUT"); continue }
            val currentSub=subscriptions.findById(a.subscriptionId).orElse(null)
            if(currentSub?.fiberOnu?.sn?.uppercase()?.let { "ONU:$it" }!=a.deviceKey ||
                (a.acsDeviceId!=null && identity.resolveAcs(a.acsDeviceId!!)!=a.subscriptionId)) {
                finish(a.id!!,"UNVERIFIED",error="IDENTITY_CHANGED"); continue
            }
            if(a.action=="WIFI_REFRESH") {
                if(wifi.findById(a.subscriptionId).orElse(null)?.takeIf { it.deviceId==a.acsDeviceId && it.qualityStatus==com.dscorp.wispadmin.servicehealth.domain.Quality.FRESH }?.observedAt?.isAfter(a.createdAt)==true) finish(a.id!!,"CONFIRMED")
            } else if(a.action in setOf("REBOOT_ACS","REBOOT_ONU") && a.acsDeviceId!=null) {
                try {
                    val cache=client.readDeviceCache(listOf(a.acsDeviceId!!),"_id,_lastInform,_lastBoot").singleOrNull()
                    val boot=cache?.let { WifiTelemetry.parseInstant(it.path("_lastBoot")) }
                    if(boot!=null && boot>a.createdAt && boot<=Instant.now()) finish(a.id!!,"CONFIRMED")
                } catch (_: Exception) { /* A later boot observation is required; never resend. */ }
            } else if(a.action=="CONFIG") {
                val device=a.acsDeviceId ?: continue
                if(identity.resolveAcs(device)!=a.subscriptionId) continue
                val expected=json.readTree(a.confirmationJson ?: "{}")
                val paths=expected.fieldNames().asSequence().toList()
                if(paths.isEmpty()) continue
                try {
                    val root=client.readDeviceCache(listOf(device),paths.joinToString(",")).singleOrNull() ?: continue
                    fun confirmed(path: String): Boolean {
                        val at=WifiTelemetry.timestamp(WifiTelemetry.node(root,path)) ?: return false
                        val value=WifiTelemetry.value(root,path) ?: return false
                        return at>a.createdAt && digest(value)==expected.path(path).asText()
                    }
                    tx.executeWithoutResult {
                        val row=actions.findById(a.id!!).get()
                        val wan=paths.filter { !it.contains(".WLANConfiguration.") }; val wireless=paths-wan.toSet()
                        if(row.networkStatus!=null && wan.isNotEmpty() && wan.all(::confirmed)) row.networkStatus="CONFIRMED"
                        if(row.wifiStatus!=null && wireless.isNotEmpty() && wireless.all(::confirmed)) row.wifiStatus="CONFIRMED"
                        if(listOfNotNull(row.networkStatus,row.wifiStatus).all { it=="CONFIRMED" }) { row.status="CONFIRMED"; row.completedAt=Instant.now() }
                        actions.save(row)
                    }
                } catch (_: Exception) { /* Leave pending; never resend the command. */ }
            }
        }
        // A process crash leaves uncertainty, not a permission to retry a disruptive operation.
        for(a in actions.findByStatus("RUNNING")) if(a.createdAt<Instant.now().minusSeconds(300)) finish(a.id!!,"UNVERIFIED",error="EXECUTOR_LOST")
    }
}
