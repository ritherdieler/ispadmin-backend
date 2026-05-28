package com.dscorp.wispadmin.wispadmin.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class LoggingService {
    companion object {
        /**
         * Obtiene un logger para la clase especificada
         */
        inline fun <reified T> getLogger(): Logger {
            return LoggerFactory.getLogger(T::class.java)
        }

        /**
         * Obtiene un logger por nombre
         */
        fun getLogger(name: String): Logger {
            return LoggerFactory.getLogger(name)
        }
    }

    private val logger = getLogger<LoggingService>()

    /**
     * Registra un error en el sistema de logs
     */
    fun logError(module: String, exception: Exception, data: String? = null) {
        val stackTrace = exception.stackTraceToString()
        logger.error("Error en módulo [$module]: ${exception.message}")
        logger.error("Stacktrace: $stackTrace")
        if (data != null) {
            logger.error("Datos adicionales: $data")
        }
    }

    /**
     * Registra información en el sistema de logs
     */
    fun logInfo(module: String, message: String, data: String? = null) {
        logger.info("[$module] $message")
        if (data != null) {
            logger.info("Datos: $data")
        }
    }

    /**
     * Registra una advertencia en el sistema de logs
     */
    fun logWarning(module: String, message: String, data: String? = null) {
        logger.warn("[$module] $message")
        if (data != null) {
            logger.warn("Datos: $data")
        }
    }

    /**
     * Registra un mensaje de depuración en el sistema de logs
     */
    fun logDebug(module: String, message: String, data: String? = null) {
        logger.debug("[$module] $message")
        if (data != null) {
            logger.debug("Datos: $data")
        }
    }
} 