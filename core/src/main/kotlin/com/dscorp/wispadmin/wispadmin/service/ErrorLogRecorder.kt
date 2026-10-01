package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.controller.toErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ErrorLogRecorder(private val errorLogRepository: ErrorLogRepository) {
    private val logger = LoggerFactory.getLogger(ErrorLogRecorder::class.java)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(ex: Exception, module: Modules) {
        runCatching { errorLogRepository.save(ex.toErrorLog(module)) }
            .onFailure { logger.warn("No se pudo guardar ErrorLog de {}", module.name, it) }
    }
}
