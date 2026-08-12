package com.dscorp.wispadmin.wispadmin.logging

import com.dscorp.wispadmin.observability.config.CorrelationIdFilter
import com.dscorp.wispadmin.observability.config.TraceContextFilter
import com.dscorp.wispadmin.observability.port.ObservabilityReporter
import com.dscorp.wispadmin.observability.port.ReportedEvent
import com.dscorp.wispadmin.wispadmin.config.HttpFailureContext
import com.dscorp.wispadmin.wispadmin.data.model.ErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.dto.SmartMapValidationErrorDto
import com.dscorp.wispadmin.wispadmin.exception.SmartMapSectorValidationException
import com.dscorp.wispadmin.wispadmin.service.MapboxDirectionsException
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.util.*
import javax.persistence.EntityNotFoundException
import javax.servlet.http.HttpServletRequest

/**
 * Controlador global para manejar todas las excepciones de la aplicación
 */
@ControllerAdvice
class GlobalExceptionHandler @Autowired constructor(
    private val loggingService: LoggingService,
    private val errorLogRepository: ErrorLogRepository,
    private val observabilityReporter: ObservabilityReporter? = null
) : ResponseEntityExceptionHandler() {

    companion object {
        const val ATTR_OBS_REPORTED = "obsEventReported"
    }

    @Value("\${spring.servlet.multipart.max-file-size:8MB}")
    private lateinit var maxFileSize: String

    @ExceptionHandler(SmartMapSectorValidationException::class)
    fun handleSmartMapSectorValidation(
        ex: SmartMapSectorValidationException,
    ): ResponseEntity<SmartMapValidationErrorDto> {
        return ResponseEntity.badRequest().body(
            SmartMapValidationErrorDto(
                code = ex.code,
                message = ex.message ?: "Validacion de sector fallida.",
                sectorName = ex.sectorName,
            ),
        )
    }

    @ExceptionHandler(MapboxDirectionsException::class)
    fun handleMapboxDirections(ex: MapboxDirectionsException): ResponseEntity<Map<String, String>> {
        val detail = buildString {
            append(ex.message ?: "Error al consultar Mapbox Directions")
            val cause = ex.cause
            if (cause != null) {
                append(" | cause=")
                append(cause.javaClass.simpleName)
                append(": ")
                append(cause.message)
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            mapOf(
                "code" to "MAPBOX_DIRECTIONS_ERROR",
                "message" to detail,
            ),
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.badRequest().body(
            mapOf(
                "code" to "BAD_REQUEST",
                "message" to (ex.message ?: "Solicitud invalida"),
            ),
        )
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(ex: EntityNotFoundException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            mapOf(
                "code" to "NOT_FOUND",
                "message" to (ex.message ?: "Recurso no encontrado"),
            ),
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<Map<String, Any?>> {
        return ResponseEntity.status(ex.status).body(
            mapOf(
                "status" to ex.status.value(),
                "error" to ex.status.reasonPhrase,
                "message" to ex.reason,
            ),
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatus,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalido") }
        return ResponseEntity.badRequest().body(
            mapOf(
                "code" to "VALIDATION_ERROR",
                "message" to "Datos de entrada invalidos",
                "errors" to errors,
            ),
        )
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(
        ex: MaxUploadSizeExceededException,
        request: WebRequest
    ): ResponseEntity<Any> {
        val httpRequest = (request as ServletWebRequest).request
        val path = httpRequest.requestURI
        val method = httpRequest.method
        val ip = httpRequest.remoteAddr
        val userAgent = httpRequest.getHeader("User-Agent") ?: "Unknown"
        val moduleName = extractModuleFromRequest(httpRequest)

        loggingService.logError(
            module = moduleName,
            exception = ex,
            data = "URL: $path, Method: $method, IP: $ip, Agent: $userAgent"
        )

        httpRequest.setAttribute(HttpFailureContext.ATTR_STACK_SUMMARY, StackTraceSummarizer.summarize(ex))
        reportToObservability(httpRequest, ex, HttpStatus.PAYLOAD_TOO_LARGE.value())

        val errorResponse = mapOf(
            "timestamp" to Date(),
            "status" to HttpStatus.PAYLOAD_TOO_LARGE.value(),
            "error" to "El archivo supera el tamaño máximo permitido ($maxFileSize)",
            "path" to path
        )

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse)
    }

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
        reportToObservability(httpRequest, ex, HttpStatus.INTERNAL_SERVER_ERROR.value())

        val errorResponse = mapOf(
            "timestamp" to Date(),
            "status" to HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "error" to "Error interno del servidor",
            "path" to path
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    private fun reportToObservability(request: HttpServletRequest, ex: Exception, status: Int) {
        val reporter = observabilityReporter ?: return
        try {
            request.setAttribute(ATTR_OBS_REPORTED, true)
            val correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE) as? String
                ?: request.getHeader(CorrelationIdFilter.HEADER)
            val sessionId = request.getAttribute(TraceContextFilter.ATTRIBUTE_SESSION) as? String
                ?: request.getHeader(TraceContextFilter.HEADER_SESSION)
            reporter.report(
                ReportedEvent(
                    eventType = "error",
                    platform = "backend",
                    severity = if (status >= 500) "error" else "warning",
                    message = ex.message,
                    errorType = ex.javaClass.name,
                    stacktrace = ex.stackTraceToString(),
                    correlationId = correlationId,
                    sessionId = sessionId,
                    url = request.requestURI,
                    httpMethod = request.method,
                    httpStatus = status,
                    userAgent = request.getHeader("User-Agent")
                )
            )
        } catch (_: Exception) {
        }
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
            path.contains("/smart-map") -> Modules.DASHBOARD.name
            else -> Modules.GENERAL.name
        }
    }
}
