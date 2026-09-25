package com.dscorp.wispadmin.acs.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

data class AcsErrorResponse(val error: String, val message: String)

@RestControllerAdvice(basePackages = ["com.dscorp.wispadmin.acs"])
class AcsExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(ex: ResponseStatusException): ResponseEntity<AcsErrorResponse> =
        ResponseEntity.status(ex.status)
            .body(AcsErrorResponse(error = ex.status.toString(), message = detail(ex.reason ?: ex.message)))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleArgument(ex: IllegalArgumentException): ResponseEntity<AcsErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(AcsErrorResponse(error = "invalid_request", message = detail(ex.message)))

    @ExceptionHandler(IllegalStateException::class)
    fun handleState(ex: IllegalStateException): ResponseEntity<AcsErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(AcsErrorResponse(error = "operation_rejected", message = detail(ex.message)))

    private fun detail(value: String?): String {
        val text = value?.takeIf { it.isNotBlank() } ?: "Error interno del servidor"
        return text.replace(SECRET, "$1=<redacted>").replace(Regex("\\s+"), " ").take(500)
    }

    private companion object {
        val SECRET = Regex("(?i)(password|passwd|passphrase|secret|authorization)(\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+")
    }
}
