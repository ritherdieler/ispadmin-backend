package com.dscorp.wispadmin.wispadmin.util

import org.slf4j.LoggerFactory
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component

@Component
class RequestResponseLoggingInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(
        request: org.springframework.http.HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        // Log de la solicitud antes de enviarla
        println("Solicitud HTTP ${request.method} a ${request.uri}")
        println("Cabeceras de la solicitud: ${request.headers}")
        println("Cuerpo de la solicitud: ${String(body)}")

        val response = execution.execute(request, body)

        // Log de la respuesta después de recibirla
        println("Respuesta HTTP ${response.statusCode} de ${request.uri}")
        println("Cabeceras de la respuesta: ${response.headers}")
        // Aquí puedes registrar el cuerpo de la respuesta si es necesario
//        println("Cuerpo de la respuesta: ${String(response.body.readAllBytes())}")

        return response
    }
}