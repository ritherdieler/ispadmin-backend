package com.dscorp.wispadmin.transport

import org.slf4j.MDC
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class RegistrationTimingClientInterceptor(
    private val timing: RegistrationTiming,
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        if (!timing.isEnabled) {
            return execution.execute(request, body)
        }
        val trace = MDC.get(RegistrationTimingSupport.MDC_TRACE)
        if (!trace.isNullOrBlank() && request.headers.getFirst(RegistrationTimingSupport.HEADER).isNullOrBlank()) {
            request.headers.set(RegistrationTimingSupport.HEADER, trace)
        }
        val start = System.currentTimeMillis()
        var status: Int? = null
        try {
            val response = execution.execute(request, body)
            status = runCatching { response.rawStatusCode }.getOrNull()
            return response
        } finally {
            val path = runCatching { request.uri.path }.getOrNull() ?: request.uri.toString()
            timing.httpOut(
                method = request.method?.name ?: "HTTP",
                path = path,
                status = status,
                ms = System.currentTimeMillis() - start,
            )
        }
    }
}
