package com.dscorp.wispadmin.oltgateway.exception

import com.dscorp.wispadmin.oltgateway.dto.ErrorResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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
}
