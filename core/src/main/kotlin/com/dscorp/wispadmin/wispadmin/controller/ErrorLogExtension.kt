package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.ErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.logging.LoggingService
import java.util.*

/**
 * Extensión para convertir una excepción en un ErrorLog para guardar en BD
 */
fun Exception.toErrorLog(module: Modules): ErrorLog {
    val moduleName = if (this is ModuleException) this.moduleName else module.name

    // Usar servicio de logging (si está disponible) para registrar el error en los archivos log
    runCatching {
        val loggingService = LoggingService()
        loggingService.logError(moduleName, this)
    }

    // Crear objeto para guardar en BD
    val error = ErrorLog(
        module = moduleName,
        error = this.message,

        data = "",
        date = Date()
    )

    return error
}

class ModuleException(override val message: String, val moduleName: String) : Exception(message) 