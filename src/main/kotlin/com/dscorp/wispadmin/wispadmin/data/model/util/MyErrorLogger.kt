package com.dscorp.wispadmin.wispadmin.data.model.util

import com.dscorp.wispadmin.wispadmin.data.model.ErrorLog
import com.dscorp.wispadmin.wispadmin.logging.LoggingService
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler


//@ControllerAdvice
//class MyErrorLogger @Autowired constructor(
//    private val repository: ErrorLogRepository,
//    private val loggingService: LoggingService
//) {
//    @ExceptionHandler(Exception::class)
//    fun handleException(ex: Exception) {
//        // Primero log a archivo con SLF4J/Logback
//        val moduleName = if (ex is ModuleException) ex.moduleName else "UNKNOWN"
//        loggingService.logError(moduleName, ex)
//
//        // También guardar en base de datos para compatibilidad con código existente
//        val error = ErrorLog(error = ex.message, stackTrace = ex.stackTraceToString())
//        if (ex is ModuleException) {
//            error.module = ex.moduleName
//        }
//        try {
//            repository.save(error)
//        } catch (e: Exception) {
//            // Si falla el guardado en BD, al menos ya tenemos el log en archivo
//            loggingService.logError("ERROR_LOGGER", e, "Error al guardar excepción en base de datos")
//        }
//    }
//}
//
//class ModuleException(val moduleName: String, val msj: String) : Exception(msj)