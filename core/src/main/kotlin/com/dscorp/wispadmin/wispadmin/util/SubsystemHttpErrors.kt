package com.dscorp.wispadmin.wispadmin.util

import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException

class SubsystemFailure(val source: String,val code: String,status: HttpStatus) : ResponseStatusException(status,code)
object SubsystemHttpErrors {
    fun translate(error: RestClientException,source: String): SubsystemFailure {
        if(error is com.dscorp.wispadmin.transport.InvalidSubsystemResponse) return SubsystemFailure(source,"UPSTREAM_CONTRACT_ERROR",HttpStatus.BAD_GATEWAY)
        if(generateSequence<Throwable>(error) { it.cause }.any { it is java.net.SocketTimeoutException }) return SubsystemFailure(source,"UPSTREAM_TIMEOUT",HttpStatus.GATEWAY_TIMEOUT)
        if(error !is RestClientResponseException) return SubsystemFailure(source,"UPSTREAM_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE)
        val status=when(error.rawStatusCode) {
            400,404,409,422,429 -> HttpStatus.valueOf(error.rawStatusCode)
            504 -> HttpStatus.GATEWAY_TIMEOUT
            else -> HttpStatus.BAD_GATEWAY
        }
        val code=when(error.rawStatusCode) {
            401,403 -> "UPSTREAM_AUTHENTICATION_FAILED"
            404 -> "UPSTREAM_NOT_FOUND"
            400,422 -> "UPSTREAM_VALIDATION_FAILED"
            409 -> "UPSTREAM_CONFLICT"
            429 -> "UPSTREAM_RATE_LIMITED"
            504 -> "UPSTREAM_TIMEOUT"
            else -> "UPSTREAM_FAILURE"
        }
        return SubsystemFailure(source,code,status)
    }
}
