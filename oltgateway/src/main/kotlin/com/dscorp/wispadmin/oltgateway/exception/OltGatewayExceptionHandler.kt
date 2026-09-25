package com.dscorp.wispadmin.oltgateway.exception

import com.dscorp.wispadmin.oltgateway.dto.ErrorResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice(basePackages = ["com.dscorp.wispadmin.oltgateway"])
class OltGatewayExceptionHandler {

    @ExceptionHandler(OltUnreachableException::class)
    fun handleUnreachable(ex: OltUnreachableException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponseDto(error = "olt_unreachable", message = ex.message ?: "OLT unreachable"))
    }

    @ExceptionHandler(OnuNotFoundException::class)
    fun handleNotFound(ex: OnuNotFoundException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponseDto(error = "onu_not_found", message = ex.message ?: "ONU not found"))
    }

    @ExceptionHandler(OltCommandTimeoutException::class)
    fun handleTimeout(ex: OltCommandTimeoutException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(ErrorResponseDto(error = "olt_timeout", message = ex.message ?: "OLT command timeout"))
    }

    @ExceptionHandler(InvalidOltGatewayApiKeyException::class)
    fun handleUnauthorized(ex: InvalidOltGatewayApiKeyException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponseDto(error = "unauthorized", message = ex.message ?: "Invalid API key"))
    }

    @ExceptionHandler(OltWritesDisabledException::class)
    fun handleWritesDisabled(ex: OltWritesDisabledException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponseDto(error = "writes_disabled", message = ex.message ?: "Writes disabled"))
    }

    @ExceptionHandler(OltGatewayConflictException::class)
    fun handleConflict(ex: OltGatewayConflictException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponseDto(error = "conflict", message = ex.message ?: "Conflict"))
    }

    @ExceptionHandler(CliBusBusyException::class)
    fun handleCliBusBusy(ex: CliBusBusyException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponseDto(error = "cli_bus_busy", message = ex.reason))
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(ex: ResponseStatusException): ResponseEntity<ErrorResponseDto> =
        ResponseEntity.status(ex.status)
            .body(ErrorResponseDto(error = ex.status.toString(), message = detail(ex.reason ?: ex.message)))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponseDto> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponseDto(error = "invalid_request", message = detail(ex.message)))

    @ExceptionHandler(IllegalStateException::class)
    fun handleState(ex: IllegalStateException): ResponseEntity<ErrorResponseDto> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponseDto(error = "operation_rejected", message = detail(ex.message)))

    private fun detail(value: String?): String {
        val text = value?.takeIf { it.isNotBlank() } ?: "Error interno del servidor"
        return text.replace(SECRET, "$1=<redacted>").replace(Regex("\\s+"), " ").take(500)
    }

    private companion object {
        val SECRET = Regex("(?i)(password|passwd|passphrase|secret|authorization)(\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+")
    }
}
