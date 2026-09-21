package com.dscorp.wispadmin.wispadmin.logging

import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.ServletWebRequest
import javax.servlet.http.HttpServletRequest

class GlobalExceptionHandlerTest {

    private val loggingService = mockk<LoggingService>(relaxed = true)
    private val errorLogRepository = mockk<ErrorLogRepository>(relaxed = true)
    private val handler = GlobalExceptionHandler(loggingService, errorLogRepository, null)

    @Test
    fun `uncaught exception returns detailed message for clients`() {
        every { errorLogRepository.save(any()) } answers { firstArg() }
        val request = servletRequest("/ispadmin/subscription/with-facade-photo")

        val response = handler.handleAllExceptions(
            RuntimeException(
                "Error al registrar la suscripción: La ONU HWTC15F610C6 ya está asignada a la suscripción ACTIVE #1895."
            ),
            request,
        )

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(
            "Error al registrar la suscripción: La ONU HWTC15F610C6 ya está asignada a la suscripción ACTIVE #1895.",
            body["message"],
        )
        assertEquals(
            "Error al registrar la suscripción: La ONU HWTC15F610C6 ya está asignada a la suscripción ACTIVE #1895.",
            body["error"],
        )
        assertEquals(500, body["status"])
    }

    @Test
    fun `illegal state returns conflict with detailed message`() {
        val request = servletRequest("/ispadmin/subscription/with-facade-photo")

        val response = handler.handleIllegalState(
            IllegalStateException(
                "La ONU HWTC15F610C6 ya está asignada a la suscripción ACTIVE #1895. Cancele ese servicio antes de registrar otra."
            ),
            request,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals(
            "La ONU HWTC15F610C6 ya está asignada a la suscripción ACTIVE #1895. Cancele ese servicio antes de registrar otra.",
            body["message"],
        )
        assertEquals(
            "La ONU HWTC15F610C6 ya está asignada a la suscripción ACTIVE #1895. Cancele ese servicio antes de registrar otra.",
            body["error"],
        )
        assertEquals(409, body["status"])
        assertEquals("CONFLICT", body["code"])
    }

    private fun servletRequest(path: String): ServletWebRequest {
        val httpRequest = mockk<HttpServletRequest>(relaxed = true)
        every { httpRequest.requestURI } returns path
        every { httpRequest.method } returns "POST"
        every { httpRequest.remoteAddr } returns "127.0.0.1"
        every { httpRequest.getHeader(any()) } returns null
        every { httpRequest.getAttribute(any()) } returns null
        every { httpRequest.setAttribute(any(), any()) } just runs
        val webRequest = mockk<ServletWebRequest>()
        every { webRequest.request } returns httpRequest
        return webRequest
    }
}
