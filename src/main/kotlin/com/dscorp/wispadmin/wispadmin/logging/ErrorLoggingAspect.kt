package com.dscorp.wispadmin.wispadmin.logging

import com.dscorp.wispadmin.wispadmin.data.model.ErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Aspect
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*

/**
 * Aspecto que captura excepciones en cualquier método de la aplicación
 */
@Aspect
@Component
class ErrorLoggingAspect @Autowired constructor(
    private val loggingService: LoggingService,
    private val errorLogRepository: ErrorLogRepository
) {

    /**
     * Captura excepciones en todos los métodos de los servicios
     */
    @AfterThrowing(
        pointcut = "execution(* com.dscorp.wispadmin.wispadmin.service.*.*(..))",
        throwing = "ex"
    )
    fun logServiceException(joinPoint: JoinPoint, ex: Exception) {
        logException(joinPoint, ex, "SERVICE")
    }

    /**
     * Captura excepciones en todos los métodos de los repositorios
     */
    @AfterThrowing(
        pointcut = "execution(* com.dscorp.wispadmin.wispadmin.repository.*.*(..))",
        throwing = "ex"
    )
    fun logRepositoryException(joinPoint: JoinPoint, ex: Exception) {
        logException(joinPoint, ex, "REPOSITORY")
    }
    
    /**
     * Captura excepciones en todos los métodos de los controladores
     * (respaldo adicional al GlobalExceptionHandler)
     */
    @AfterThrowing(
        pointcut = "execution(* com.dscorp.wispadmin.wispadmin.controller.*.*(..))",
        throwing = "ex"
    )
    fun logControllerException(joinPoint: JoinPoint, ex: Exception) {
        logException(joinPoint, ex, "CONTROLLER")
    }
    
    /**
     * Captura excepciones en utilidades y otros componentes
     */
    @AfterThrowing(
        pointcut = "execution(* com.dscorp.wispadmin.wispadmin.util.*.*(..))",
        throwing = "ex"
    )
    fun logUtilException(joinPoint: JoinPoint, ex: Exception) {
        logException(joinPoint, ex, "UTIL")
    }

    /**
     * Método común para registrar las excepciones
     */
    private fun logException(joinPoint: JoinPoint, ex: Exception, type: String) {
        val className = joinPoint.target.javaClass.simpleName
        val methodName = joinPoint.signature.name
        val arguments = joinPoint.args.joinToString(", ") { it?.toString() ?: "null" }
        
        // Determinar el módulo basado en el nombre de la clase
        val moduleName = getModuleFromClassName(className)
        
        // Crear información contextual
        val contextInfo = "Exception in $type - Class: $className, Method: $methodName, Args: $arguments"
        
        // Registrar en el archivo de log
        loggingService.logError(moduleName, ex, contextInfo)
        
        // Temporalmente desactivado el guardado en base de datos
        /*
        val errorLog = ErrorLog(
            module = moduleName,
            error = ex.message,
            data = contextInfo,
            date = Date()
        )
        
        if (!className.contains("ErrorLogRepository")) {
            try {
                errorLogRepository.save(errorLog)
            } catch (e: Exception) {
                loggingService.logError(Modules.GENERAL.name, e, "Error al guardar en base de datos")
            }
        }
        */
    }
    
    /**
     * Determina el módulo a partir del nombre de la clase
     */
    private fun getModuleFromClassName(className: String): String {
        return when {
            className.contains("User") -> Modules.USER.name
            className.contains("Customer") -> Modules.CUSTOMER.name
            className.contains("Subscription") -> Modules.SUBSCRIPTION.name
            className.contains("Plan") -> Modules.PLAN.name
            className.contains("Admin") -> Modules.ADMIN.name
            className.contains("Dashboard") -> Modules.DASHBOARD.name
            className.contains("Billing") -> Modules.BILLING.name
            className.contains("Payment") -> Modules.PAYMENT.name
            className.contains("Outlay") -> Modules.OUTLAY.name
            className.contains("Izipay") -> Modules.IZIPAY.name
            className.contains("Microtic") -> Modules.MICROTIC.name
            className.contains("LogViewer") -> Modules.LOG_VIEWER.name
            className.contains("Ticket") || className.contains("Assistance") -> Modules.ASSISTANCE_TICKET.name
            else -> Modules.GENERAL.name
        }
    }
} 