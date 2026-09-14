package com.dscorp.wispadmin.wispadmin.aspect

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.reflect.Parameter
import java.util.*

@Aspect
@Component
@Order(0)
class ControllerLoggingAspect {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val errorLog = LoggerFactory.getLogger("ERROR_LOGGER")
    
    // ANSI color codes
    private val ANSI_RESET = "\u001B[0m"
    private val ANSI_RED = "\u001B[31m"
    
    // Controllers or methods to exclude from logging
    private val excludedEndpoints = listOf(
        "LogViewerController"   // Exclude the entire log viewer controller
    )

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    fun controllerPointcut() {
        // Method is empty as this is just a Pointcut
    }

    /**
     * @Around rompe funciones suspend de Kotlin: proceed() devuelve COROUTINE_SUSPENDED
     * y la respuesta HTTP queda vacia. Los metodos suspend se registran con Before/AfterReturning.
     */
    @Pointcut("execution(* *(.., kotlin.coroutines.Continuation+))")
    fun kotlinSuspendMethodPointcut() {
        // Method is empty as this is just a Pointcut
    }

    @Around("controllerPointcut() && !kotlinSuspendMethodPointcut()")
    @Throws(Throwable::class)
    fun logAround(joinPoint: ProceedingJoinPoint): Any? {
        val methodSignature = joinPoint.signature as MethodSignature
        val className = methodSignature.declaringType.simpleName
        val methodName = methodSignature.name
        
        // Skip logging for excluded endpoints
        if (shouldSkipLogging(className, methodName)) {
            return joinPoint.proceed()
        }
        
        val parameterNames = methodSignature.parameterNames
        val args = joinPoint.args
        val parameters = methodSignature.method.parameters
        
        val requestId = getRequestId()
        
        val params = buildParameterString(parameterNames, args, parameters)
        log.info("Controller [{}] - {}.{}({}): Method started", requestId, className, methodName, params)
        
        try {
            val result = joinPoint.proceed()
            
            // Check for error status codes in response entities
            val resultStr = if (result != null) {
                val resultString = truncateIfNeeded(result.toString())
                if (isErrorResponseEntity(result)) {
                    "$ANSI_RED$resultString$ANSI_RESET"  // Add red color to error responses
                } else {
                    resultString
                }
            } else "void"
            
            log.info("Controller [{}] - {}.{}: Method completed. Result: {}", requestId, className, methodName, resultStr)
            return result
        } catch (e: Exception) {
            // Obtener todos los detalles y registrarlos en el archivo de errores
            val errorDetails = extractErrorDetails(requestId, className, methodName, parameterNames, args, parameters)
            errorLog.error("ERROR DETAILS [{}]: {}", requestId, errorDetails)
            log.error("Controller [{}] - {}.{}: Exception occurred: {}", requestId, className, methodName, e.message, e)
            throw e
        }
    }

    private fun isErrorResponseEntity(result: Any): Boolean {
        if (result is ResponseEntity<*>) {
            val statusCode = result.statusCode.value()
            return statusCode >= 400
        }
        return result.toString().contains("<500,") || 
               result.toString().contains("<4") // Match 4xx status codes
    }

    @org.aspectj.lang.annotation.Before("controllerPointcut() && kotlinSuspendMethodPointcut()")
    fun logSuspendBefore(joinPoint: JoinPoint) {
        val methodSignature = joinPoint.signature as MethodSignature
        val className = methodSignature.declaringType.simpleName
        val methodName = methodSignature.name

        if (shouldSkipLogging(className, methodName)) {
            return
        }

        val requestId = getRequestId()
        val params = buildParameterString(
            methodSignature.parameterNames,
            joinPoint.args,
            methodSignature.method.parameters,
        )
        log.info("Controller [{}] - {}.{}({}): Method started (suspend)", requestId, className, methodName, params)
    }

    @org.aspectj.lang.annotation.AfterReturning(
        pointcut = "controllerPointcut() && kotlinSuspendMethodPointcut()",
        returning = "result",
    )
    fun logSuspendAfterReturning(joinPoint: JoinPoint, result: Any?) {
        val methodSignature = joinPoint.signature as MethodSignature
        val className = methodSignature.declaringType.simpleName
        val methodName = methodSignature.name

        if (shouldSkipLogging(className, methodName)) {
            return
        }

        val requestId = getRequestId()
        val resultStr = if (result != null) {
            val resultString = truncateIfNeeded(result.toString())
            if (isErrorResponseEntity(result)) {
                "$ANSI_RED$resultString$ANSI_RESET"
            } else {
                resultString
            }
        } else {
            "void"
        }
        log.info("Controller [{}] - {}.{}: Method completed (suspend). Result: {}", requestId, className, methodName, resultStr)
    }

    @AfterThrowing(pointcut = "controllerPointcut()", throwing = "exception")
    fun logAfterThrowing(joinPoint: JoinPoint, exception: Throwable) {
        val methodSignature = joinPoint.signature as MethodSignature
        val className = methodSignature.declaringType.simpleName
        val methodName = methodSignature.name
        
        // Skip logging for excluded endpoints
        if (shouldSkipLogging(className, methodName)) {
            return
        }
        
        val requestId = getRequestId()
        val args = joinPoint.args
        val parameterNames = methodSignature.parameterNames
        val parameters = methodSignature.method.parameters
        
        // Obtener todos los detalles y registrarlos en el archivo de errores
        val errorDetails = extractErrorDetails(requestId, className, methodName, parameterNames, args, parameters)
        errorLog.error("UNCAUGHT ERROR DETAILS [{}]: {}", requestId, errorDetails)
        
        log.error("Controller [{}] - {}.{}: Uncaught exception: {}", requestId, className, methodName, exception.message, exception)
    }
    
    private fun shouldSkipLogging(className: String, methodName: String): Boolean {
        return excludedEndpoints.any { excluded ->
            className.contains(excluded)
        }
    }
    
    private fun extractErrorDetails(
        requestId: String,
        className: String,
        methodName: String,
        parameterNames: Array<String>,
        args: Array<Any>,
        parameters: Array<Parameter>
    ): String {
        val sb = StringBuilder()
        sb.append("Error in ").append(className).append(".").append(methodName).append("\n")
        sb.append("RequestId: ").append(requestId).append("\n")
        
        // Obtener parámetros de consulta de la solicitud actual
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        if (request != null) {
            sb.append("URL: ").append(request.method).append(" ")
                .append(request.requestURI)
                .append(if (request.queryString != null) "?" + request.queryString else "")
                .append("\n")
                
            sb.append("Query Parameters:\n")
            request.parameterMap.forEach { (key, values) ->
                sb.append("  ").append(key).append(": ").append(values.joinToString(", ")).append("\n")
            }
        }
        
        // Detallar los argumentos del método, marcando especialmente los @RequestBody
        sb.append("Method Arguments:\n")
        for (i in parameterNames.indices) {
            if (i < args.size && i < parameters.size) {
                val paramName = parameterNames[i]
                val isRequestBody = parameters[i].isAnnotationPresent(RequestBody::class.java)
                val argValue = args[i]
                
                sb.append("  ").append(paramName)
                    .append(if (isRequestBody) " [@RequestBody]" else "")
                    .append(": ")
                
                // Mostrar el contenido completo para debugging
                sb.append(if (argValue != null) {
                    try {
                        argValue.toString().replace("\n", "\n    ")
                    } catch (e: Exception) {
                        "Error serializing object: ${e.message}"
                    }
                } else "null")
                
                sb.append("\n")
            }
        }
        
        return sb.toString()
    }
    
    private fun buildParameterString(
        parameterNames: Array<String>, 
        args: Array<Any>,
        parameters: Array<Parameter>
    ): String {
        return parameterNames.mapIndexed { index, name ->
            val argValue = if (args.size > index) {
                val value = args[index]
                val isRequestBody = parameters[index].isAnnotationPresent(RequestBody::class.java)
                if (isRequestBody) {
                    "[RequestBody]=" + truncateIfNeeded(value?.toString() ?: "null")
                } else {
                    truncateIfNeeded(value?.toString() ?: "null")
                }
            } else "null"
            "$name=$argValue"
        }.joinToString(", ")
    }
    
    private fun truncateIfNeeded(value: String, maxLength: Int = 2000): String {
        return if (value.length > maxLength) value.substring(0, maxLength) + "..." else value
    }
    
    private fun getRequestId(): String {
        return try {
            val requestAttributes = RequestContextHolder.getRequestAttributes() as ServletRequestAttributes?
            requestAttributes?.request?.getAttribute("requestId") as? String ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
} 