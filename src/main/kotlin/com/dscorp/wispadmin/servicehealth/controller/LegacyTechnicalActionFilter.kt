package com.dscorp.wispadmin.servicehealth.controller

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.service.RemoteActionService
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.core.annotation.Order
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/** Covers existing aliases so the 360's limits cannot be bypassed by using another controller. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 19)
class LegacyTechnicalActionFilter(private val properties: ServiceHealthProperties,private val scope: ServiceHealthScope,private val access: HealthAccess,
    private val actions: RemoteActionService,private val identity: IdentityService,private val onuPort: ObjectProvider<HealthOnuPort>,
    private val json: ObjectMapper): OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest,response: HttpServletResponse,chain: FilterChain) {
        val path=request.servletPath.trimEnd('/')
        val subscription=Regex("^/subscription/(\\d+)/acs/(reboot|retry-tr069)$").matchEntire(path)
        val onu=Regex("^/onu/configured/([^/]+)/reboot$").matchEntire(path)
        val compat=Regex("^/api/olt-gateway/onu/reboot/([^/]+)$").matchEntire(path)
        val rebootSubscription=path=="/subscription/reboot-fiber-onu"
        if(!properties.enabled || request.method !in setOf("POST","PUT") || (subscription==null && onu==null && compat==null && !rebootSubscription)) {
            chain.doFilter(request,response); return
        }
        try {
            val id=when {
                subscription!=null -> subscription.groupValues[1].toInt()
                rebootSubscription -> request.getParameter("subscriptionId")?.toIntOrNull()
                else -> (onu ?: compat)?.groupValues?.get(1)?.let { externalId ->
                    onuPort.ifAvailable?.findByExternalId(externalId)?.sn?.let(identity::resolveOnu)
                }
            }
            // Unmapped aliases cannot safely be attributed to an operator/subscription.
            if(id==null) throw ResponseStatusException(HttpStatus.CONFLICT,"Identidad de ONU no resuelta")
            if(!scope.collects(id)) throw ResponseStatusException(HttpStatus.CONFLICT,"Acción no disponible para esta suscripción")
            val retry=subscription?.groupValues?.get(2)=="retry-tr069"
            val actor=access.require(request,retry)
            if(retry && !properties.configEnabled) throw ResponseStatusException(HttpStatus.CONFLICT,"Configuración deshabilitada")
            val key=access.confirmation(request)
            val action=if(retry) "CONFIG" else if(subscription!=null) "REBOOT_ACS" else "REBOOT_ONU"
            val (audit,created)=actions.reserve(id,actor,key,action,actions.digest("$action:$id"),subscription!=null)
            if(!created) {
                response.status=202; response.contentType="application/json"; json.writeValue(response.writer,actions.result(audit)); return
            }
            response.setHeader("X-Service-Health-Action-Id",audit.id.toString())
            try {
                chain.doFilter(request,response)
                actions.finish(audit.id!!,if(response.status>=400) "FAILED" else "PENDING",error=if(response.status>=400) "LEGACY_ACTION_FAILED" else null)
            } catch(ex: Exception) {
                actions.finish(audit.id!!,"UNVERIFIED",error="LEGACY_ACTION_UNCONFIRMED"); throw ex
            }
        } catch(ex: ResponseStatusException) {
            if(!response.isCommitted) { response.status=ex.rawStatusCode; response.contentType="application/json"; json.writeValue(response.writer,mapOf("error" to ex.reason)) }
        }
    }
}
