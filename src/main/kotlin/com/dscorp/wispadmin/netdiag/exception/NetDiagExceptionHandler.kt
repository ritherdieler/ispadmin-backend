package com.dscorp.wispadmin.netdiag.exception

import com.dscorp.wispadmin.netdiag.dto.ErrorResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.dscorp.wispadmin.netdiag"])
class NetDiagExceptionHandler {

    @ExceptionHandler(IncidentNotFoundException::class)
    fun handleNotFound(ex: IncidentNotFoundException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponseDto(error = "incident_not_found", message = ex.message ?: "Incident not found"))
    }

    @ExceptionHandler(InvalidNetDiagApiKeyException::class)
    fun handleUnauthorized(ex: InvalidNetDiagApiKeyException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponseDto(error = "unauthorized", message = ex.message ?: "Invalid API key"))
    }

    @ExceptionHandler(NetDiagConflictException::class)
    fun handleConflict(ex: NetDiagConflictException): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponseDto(error = "conflict", message = ex.message ?: "Conflict"))
    }
}
