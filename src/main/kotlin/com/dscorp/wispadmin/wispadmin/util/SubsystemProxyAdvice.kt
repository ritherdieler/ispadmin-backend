package com.dscorp.wispadmin.wispadmin.util

import com.dscorp.wispadmin.wispadmin.controller.Tr069ModelProfileController
import com.dscorp.wispadmin.wispadmin.oltclient.OnuFacadeController
import com.dscorp.wispadmin.wispadmin.trafficclient.SubscriptionTrafficFacadeController
import com.dscorp.wispadmin.wispadmin.trafficclient.BandwidthIntelligenceFacadeController
import org.slf4j.MDC
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID
import javax.servlet.http.HttpServletRequest

@RestControllerAdvice(assignableTypes=[OnuFacadeController::class,SubscriptionTrafficFacadeController::class,BandwidthIntelligenceFacadeController::class,Tr069ModelProfileController::class])
class SubsystemProxyAdvice {
    @ExceptionHandler(SubsystemFailure::class)
    fun failure(error: SubsystemFailure,request: HttpServletRequest): ResponseEntity<SubsystemErrorBody> {
        val correlation=MDC.get("correlationId") ?: UUID.randomUUID().toString()
        val retryable=request.method=="GET" && error.code in setOf("UPSTREAM_TIMEOUT","UPSTREAM_UNAVAILABLE","UPSTREAM_RATE_LIMITED","UPSTREAM_FAILURE")
        return ResponseEntity.status(error.status).header("X-Correlation-Id",correlation)
            .body(SubsystemErrorBody(error.code,error.source,correlation,retryable))
    }
}
data class SubsystemErrorBody(val code: String,val source: String,val correlationId: String,val retryable: Boolean)
