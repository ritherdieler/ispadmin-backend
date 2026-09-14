package com.dscorp.wispadmin.wispadmin.tracing

import org.springframework.http.client.ClientHttpRequestInterceptor

object TracingInterceptorHolder {
    @Volatile
    var instance: ClientHttpRequestInterceptor? = null
}
