package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.tracing.TracingInterceptorHolder
import org.slf4j.MDC
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class OutboundCorrelationInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        MDC.get(CorrelationIdFilter.MDC_KEY)?.let { request.headers.set(CorrelationIdFilter.HEADER, it) }
        MDC.get(CorrelationIdFilter.OPERATION_MDC_KEY)?.let { request.headers.set(CorrelationIdFilter.OPERATION_HEADER, it) }
        val tracing = TracingInterceptorHolder.instance ?: return execution.execute(request, body)
        return tracing.intercept(request, body, execution)
    }
}
