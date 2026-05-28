package com.dscorp.wispadmin.wispadmin.logging

import com.dscorp.wispadmin.wispadmin.config.HttpFailureContext
import com.dscorp.wispadmin.wispadmin.data.model.ErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.util.*
import javax.servlet.http.HttpServletRequest

/**
 * Controlador global para manejar todas las excepciones de la aplicación
 */
@ControllerAdvice
class GlobalExceptionHandler @Autowired constructor(
    private val loggingService: LoggingService,
    private val errorLogRepository: ErrorLogRepository
) : ResponseEntityExceptionHandler() {

    /**
     * Maneja cualquier excepción no controlada en la aplicación
     */
    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception, request: WebRequest): ResponseEntity<Any> {
        val httpRequest = (request as ServletWebRequest).request
        val path = httpRequest.requestURI
        val method = httpRequest.method
        val ip = httpRequest.remoteAddr
        val userAgent = httpRequest.getHeader("User-Agent") ?: "Unknown"
        
        // Extraer el nombre del módulo basado en la URL
        val moduleName = extractModuleFromRequest(httpRequest)
        
        // Crear registro de error para la base de datos (mantener compatibilidad)
        val errorLog = ErrorLog(
            module = moduleName,
            error = ex.message,

            data = "URL: $path, Method: $method, IP: $ip, Agent: $userAgent",
            date = Date()
        )
        
        // Guardar en base de datos
        try {
            errorLogRepository.save(errorLog)
        } catch (e: Exception) {
            // Si falla el guardado en BD, al menos lo registramos en los logs
            loggingService.logError(Modules.GENERAL.name, e, "Error al guardar en base de datos")
        }
        
        // Registrar en los archivos de log
        loggingService.logError(
            module = moduleName,
            exception = ex,
            data = "URL: $path, Method: $method, IP: $ip, Agent: $userAgent"
        )

        httpRequest.setAttribute(HttpFailureContext.ATTR_STACK_SUMMARY, StackTraceSummarizer.summarize(ex))

        val errorResponse = mapOf(
            "timestamp" to Date(),
            "status" to HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "error" to "Error interno del servidor",
            "path" to path
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
    
    /**
     * Extrae el nombre del módulo basado en la URL de la solicitud
     */
    private fun extractModuleFromRequest(request: HttpServletRequest): String {
        val path = request.requestURI
        return when {
            path.contains("/user") -> Modules.USER.name
            path.contains("/customer") -> Modules.CUSTOMER.name
            path.contains("/subscription") -> Modules.SUBSCRIPTION.name
            path.contains("/plan") -> Modules.PLAN.name
            path.contains("/admin") -> Modules.ADMIN.name
            path.contains("/dashboard") -> Modules.DASHBOARD.name
            path.contains("/billing") -> Modules.BILLING.name
            path.contains("/payment") -> Modules.PAYMENT.name
            path.contains("/outlay") -> Modules.OUTLAY.name
            path.contains("/izipay") -> Modules.IZIPAY.name
            path.contains("/microtic") -> Modules.MICROTIC.name
            path.contains("/api/logs") -> Modules.LOG_VIEWER.name
            path.contains("/ticket") || path.contains("/assistance") -> Modules.ASSISTANCE_TICKET.name
            else -> Modules.GENERAL.name
        }
    }
} 