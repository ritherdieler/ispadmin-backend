package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.controller.HealthActor
import com.dscorp.wispadmin.servicehealth.domain.RemoteAction
import com.dscorp.wispadmin.servicehealth.port.HealthCpePort
import com.dscorp.wispadmin.servicehealth.port.HealthLabOpticalPort
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.RemoteActionRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiCurrentRepository
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
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
class RemoteActionService(
    private val properties: ServiceHealthProperties,
    private val scope: ServiceHealthScope,
    private val actions: RemoteActionRepository,
    private val cursors: HealthCursorRepository,
    private val subscriptions: SubscriptionRepository,
    private val wifi: WifiCurrentRepository,
    private val identity: IdentityService,
    private val cpe: ObjectProvider<HealthCpePort>,
    private val tx: TransactionTemplate,
    private val json: ObjectMapper,
    private val subscriptionService: SubscriptionService? = null,
    private val labOptical: ObjectProvider<HealthLabOpticalPort>? = null,
) {
    fun digest(value: String): String {
        require(properties.stationHmacKey.toByteArray().size>=32) { "HMAC_KEY_NOT_CONFIGURED" }
        val mac=Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(properties.stationHmacKey.toByteArray(),"HmacSHA256"))
        return mac.doFinal(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun reserve(id: Int,actor: HealthActor,key: String,action: String,payloadDigest: String,needsCr: Boolean): Pair<RemoteAction,Boolean> {
        if(!properties.actionsEnabled || !scope.collects(id)) throw ResponseStatusException(HttpStatus.CONFLICT,"Acciones deshabilitadas para esta suscripción")
        return tx.execute {
            cursors.lock("actions") ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Coordinador no inicializado")
            val existing=actions.findByActorIdAndRequestKey(actor.id,key)
            if(existing!=null) {
                if(existing.subscriptionId!=id || existing.action!=action || existing.requestDigest!=payloadDigest) throw ResponseStatusException(HttpStatus.CONFLICT,"Clave reutilizada con otra operación")
                return@execute existing to false
            }
            val sub=subscriptions.findById(id).orElseThrow { NoSuchElementException("Suscripción inexistente") }
            val sn=sub.fiberOnuSn ?: throw ResponseStatusException(HttpStatus.CONFLICT,"ONU sin identidad")
            if(identity.resolveOnu(sn)!=id) throw ResponseStatusException(HttpStatus.CONFLICT,"Identidad ONU ambigua")
            val deviceKey="ONU:${sn.uppercase()}"
            if(needsCr && cpe.ifAvailable==null) throw ResponseStatusException(HttpStatus.CONFLICT,"Identidad ACS no resuelta")
            val now=Instant.now()
            val previous=actions.findTopByDeviceKeyAndActionInOrderByCreatedAtDesc(deviceKey,
                if(action=="WIFI_REFRESH") listOf("WIFI_REFRESH","CONFIG","REBOOT_ACS")
                else if(action=="OPTICAL_REFRESH") listOf("OPTICAL_REFRESH")
                else listOf("CONFIG","REBOOT_ONU","REBOOT_ACS","WIFI_REFRESH"))
            val manualRefresh = action == "WIFI_REFRESH" || action == "OPTICAL_REFRESH"
            if(!manualRefresh && previous!=null && previous.createdAt>now.minusSeconds(properties.actionCooldownSeconds))
                throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Espere diez minutos entre acciones sobre el equipo")
            if(needsCr && actions.findByStatus("RUNNING").count { it.action in setOf("WIFI_REFRESH","CONFIG","REBOOT_ACS") }>=properties.crConcurrency.coerceIn(1,3))
                throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Límite de conexiones ACS concurrentes")
            actions.saveAndFlush(RemoteAction(acsDeviceId=sn,subscriptionId=id,actorId=actor.id,requestKey=key,deviceKey=deviceKey,action=action,
                status="RUNNING",createdAt=now,requestDigest=payloadDigest)) to true
        }!!
    }
    fun result(a: RemoteAction)=ActionResult(a.id!!,a.subscriptionId,a.status,
        when(a.status) { "CONFIRMED" -> "Lectura posterior confirma la operación"; "FAILED" -> "La operación falló; revise la evidencia";
            "UNVERIFIED" -> "Operación sin confirmación; no se reintentará automáticamente"; else -> "Solicitud pendiente; aceptación no significa aplicación" },
        a.networkStatus=="CONFIRMED",a.wifiStatus=="CONFIRMED",a.networkStatus,a.wifiStatus,a.networkChannel)

    fun finish(id: Long,status: String,taskId: String?=null,error: String?=null) = tx.execute {
        val a=actions.findById(id).orElseThrow { NoSuchElementException() }
        a.status=status; if(taskId!=null) a.taskId=taskId; a.errorReason=error
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
            val sn = subscriptions.findById(id).orElse(null)?.fiberOnuSn
            val port = cpe.ifAvailable
            if (sn != null && port != null) {
                val ack = port.reboot(sn)
                if (ack.accepted) return result(finish(action.id!!, "PENDING"))
            }
            requireNotNull(subscriptionService) { "Reinicio no disponible" }.rebootFiberOnu(id)
            result(finish(action.id!!, "PENDING"))
        } catch (_: Exception) {
            result(finish(action.id!!, "UNVERIFIED", error = "OLT_REQUEST_UNCONFIRMED"))
        }
    }

    fun refreshOptical(id: Int, actor: HealthActor, key: String): ActionResult {
        if (!properties.opticalEnabled) throw ResponseStatusException(HttpStatus.CONFLICT, "Óptica deshabilitada")
        replay(id, actor, key, "OPTICAL_REFRESH", digest("OPTICAL_REFRESH:$id"))?.let { return it }
        val port = labOptical?.ifAvailable ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Óptica SSH no disponible")
        val (action, created) = reserve(id, actor, key, "OPTICAL_REFRESH", digest("OPTICAL_REFRESH:$id"), false)
        if (!created) return result(action)
        return try {
            val sn = subscriptions.findById(id).orElse(null)?.fiberOnuSn
                ?: return result(finish(action.id!!, "FAILED", error = "missing_sn"))
            val refresh = port.refreshBySn(sn)
            when {
                refresh.collected -> result(finish(action.id!!, "CONFIRMED"))
                refresh.unmapped -> result(finish(action.id!!, "FAILED", error = "ONU_UNMAPPED"))
                else -> result(finish(action.id!!, "FAILED", error = refresh.error ?: "OPTICAL_SSH_FAILED"))
            }
        } catch (_: Exception) {
            result(finish(action.id!!, "UNVERIFIED", error = "OLT_SSH_UNCONFIRMED"))
        }
    }

    fun refresh(id: Int,actor: HealthActor,key: String): ActionResult {
        replay(id,actor,key,"WIFI_REFRESH",digest("WIFI_REFRESH:$id"))?.let { return it }
        val sn = subscriptions.findById(id).orElseThrow { NoSuchElementException("Suscripción inexistente") }.fiberOnuSn
            ?: throw ResponseStatusException(HttpStatus.CONFLICT,"ONU sin identidad")
        val port = cpe.ifAvailable ?: throw ResponseStatusException(HttpStatus.CONFLICT,"Gateway CPE no disponible")
        val (action,created)=reserve(id,actor,key,"WIFI_REFRESH",digest("WIFI_REFRESH:$id"),true)
        if(!created) return result(action)
        return try {
            val ack = port.wifiRefresh(sn)
            result(finish(action.id!!,if(ack.accepted) "PENDING" else "FAILED", error = if(ack.accepted) null else ack.message ?: "ACS_REJECTED"))
        } catch (_: Exception) { result(finish(action.id!!,"UNVERIFIED",error="ACS_REQUEST_UNCONFIRMED")) }
    }

    fun configure(id: Int,actor: HealthActor,key: String,request: CpeConfiguration): ActionResult {
        replay(id,actor,key,"CONFIG",digest(json.writeValueAsString(request)))?.let { return it }
        if(!properties.configEnabled) throw ResponseStatusException(HttpStatus.CONFLICT,"Configuración deshabilitada")
        if(request.network!=null && actor.role!="ADMIN") throw ResponseStatusException(HttpStatus.FORBIDDEN,"WAN requiere ADMIN")
        require(request.network!=null || request.wifi!=null) { "Configuración vacía" }
        throw ResponseStatusException(HttpStatus.CONFLICT,"CPE_WRITE_VIA_GATEWAY_UNSUPPORTED")
    }

    @Scheduled(fixedDelayString="\${service.health.action-check-interval-ms:120000}",initialDelayString="\${service.health.action-check-initial-delay-ms:120000}")
    fun confirmPending() {
        if(!properties.enabled || !properties.actionsEnabled) return
        for(a in actions.findByStatus("PENDING")) {
            if(!scope.collects(a.subscriptionId)) continue
            if(a.createdAt<Instant.now().minusSeconds(21600)) { finish(a.id!!,"UNVERIFIED",error="CONFIRMATION_TIMEOUT"); continue }
            val currentSub=subscriptions.findById(a.subscriptionId).orElse(null)
            if(currentSub?.fiberOnuSn?.uppercase()?.let { "ONU:$it" }!=a.deviceKey) {
                finish(a.id!!,"UNVERIFIED",error="IDENTITY_CHANGED"); continue
            }
            if(a.action=="WIFI_REFRESH") {
                if(wifi.findById(a.subscriptionId).orElse(null)?.takeIf { it.qualityStatus==Quality.FRESH }?.observedAt?.isAfter(a.createdAt)==true) finish(a.id!!,"CONFIRMED")
            } else if(a.action in setOf("REBOOT_ACS","REBOOT_ONU")) {
                val sn = currentSub?.fiberOnuSn ?: continue
                val inform = cpe.ifAvailable?.telemetry(sn)?.lastInformAt
                if(inform!=null && inform>a.createdAt && inform<=Instant.now()) finish(a.id!!,"CONFIRMED")
            }
        }
        for(a in actions.findByStatus("RUNNING")) if(a.createdAt<Instant.now().minusSeconds(300)) finish(a.id!!,"UNVERIFIED",error="EXECUTOR_LOST")
    }
}
