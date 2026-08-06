package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskLog
import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.data.model.TaskExecutionStatus
import com.dscorp.wispadmin.wispadmin.dto.AddressListGenerationResultDto
import com.dscorp.wispadmin.wispadmin.dto.CutServiceResultDto
import com.dscorp.wispadmin.wispadmin.dto.MonthlyBillingCloseResultDto
import com.dscorp.wispadmin.wispadmin.repository.ScheduledTaskLogRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.QueueCreationStats
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ScheduledTaskLogService(
    private val repository: ScheduledTaskLogRepository
) {

    private val logger = LoggerFactory.getLogger(ScheduledTaskLogService::class.java)
    private val gson = Gson()

    fun logCutInternetService(result: CutServiceResultDto): ScheduledTaskLog {
        logger.info("💾 Guardando log de ejecución: CUT_INTERNET_SERVICE")
        
        val status = when {
            result.errorCount == 0 -> TaskExecutionStatus.SUCCESS
            result.errorCount < result.processedCount -> TaskExecutionStatus.PARTIAL_SUCCESS
            else -> TaskExecutionStatus.FAILED
        }

        val taskLog = ScheduledTaskLog(
            taskType = ScheduledTaskType.CUT_INTERNET_SERVICE,
            executionDate = LocalDateTime.now(),
            processedCount = result.processedCount,
            createdCount = result.createdCount,
            deletedCount = result.deletedCount,
            errorCount = result.errorCount,
            itemsGeneratedInMikrotik = result.createdCount,
            omittedByTvCable = result.omittedByTvCable,
            status = status,
            message = result.message,
            detailedResult = gson.toJson(result),
            errorMessage = if (result.errorCount > 0) "Se encontraron ${result.errorCount} errores durante la ejecución" else null
        )

        logger.info("📊 Log guardado: ${result.message}")

        return repository.save(taskLog)
    }

    fun logCutInternetServiceDebtors(result: CutServiceResultDto): ScheduledTaskLog {
        logger.info("💾 Guardando log de ejecución: CUT_INTERNET_SERVICE_DEBTORS")
        
        val status = when {
            result.errorCount == 0 -> TaskExecutionStatus.SUCCESS
            result.errorCount < result.processedCount -> TaskExecutionStatus.PARTIAL_SUCCESS
            else -> TaskExecutionStatus.FAILED
        }

        val taskLog = ScheduledTaskLog(
            taskType = ScheduledTaskType.CUT_INTERNET_SERVICE_DEBTORS,
            executionDate = LocalDateTime.now(),
            processedCount = result.processedCount,
            createdCount = result.createdCount,
            deletedCount = result.deletedCount,
            errorCount = result.errorCount,
            itemsGeneratedInMikrotik = result.createdCount,
            omittedByTvCable = result.omittedByTvCable,
            status = status,
            message = result.message,
            detailedResult = gson.toJson(result),
            errorMessage = if (result.errorCount > 0) "Se encontraron ${result.errorCount} errores durante la ejecución" else null
        )

        logger.info("📊 Log de deudores guardado: ${result.message}")

        return repository.save(taskLog)
    }

    fun logCutInternetServiceCancelled(result: CutServiceResultDto): ScheduledTaskLog {
        logger.info("💾 Guardando log de ejecución: CUT_INTERNET_SERVICE_CANCELLED")
        
        val status = when {
            result.errorCount == 0 -> TaskExecutionStatus.SUCCESS
            result.errorCount < result.processedCount -> TaskExecutionStatus.PARTIAL_SUCCESS
            else -> TaskExecutionStatus.FAILED
        }

        val taskLog = ScheduledTaskLog(
            taskType = ScheduledTaskType.CUT_INTERNET_SERVICE_CANCELLED,
            executionDate = LocalDateTime.now(),
            processedCount = result.processedCount,
            createdCount = result.createdCount,
            deletedCount = result.deletedCount,
            errorCount = result.errorCount,
            itemsGeneratedInMikrotik = result.createdCount,
            omittedByTvCable = result.omittedByTvCable,
            status = status,
            message = result.message,
            detailedResult = gson.toJson(result),
            errorMessage = if (result.errorCount > 0) "Se encontraron ${result.errorCount} errores durante la ejecución" else null
        )

        logger.info("📊 Log de cancelados guardado: ${result.message}")

        return repository.save(taskLog)
    }

    fun logAddressListGeneration(result: AddressListGenerationResultDto): ScheduledTaskLog {
        logger.info("💾 Guardando log de ejecución: GENERATE_ADDRESS_LIST_CANCELLED")
        
        val status = when {
            result.errorCount == 0 -> TaskExecutionStatus.SUCCESS
            result.errorCount < result.processedCount -> TaskExecutionStatus.PARTIAL_SUCCESS
            else -> TaskExecutionStatus.FAILED
        }

        val taskLog = ScheduledTaskLog(
            taskType = ScheduledTaskType.GENERATE_ADDRESS_LIST_CANCELLED,
            executionDate = LocalDateTime.now(),
            processedCount = result.processedCount,
            createdCount = result.createdCount,
            deletedCount = result.deletedCount,
            alreadyExistsCount = result.alreadyExistsCount,
            errorCount = result.errorCount,
            itemsGeneratedInMikrotik = result.createdCount,
            status = status,
            message = result.message,
            detailedResult = gson.toJson(result),
            errorMessage = if (result.failedSubscriptions.isNotEmpty()) 
                "Suscripciones fallidas: ${result.failedSubscriptions.size}" else null
        )

        return repository.save(taskLog)
    }

    fun logQueueCreation(result: QueueCreationStats): ScheduledTaskLog {
        logger.info("💾 Guardando log de ejecución: CREATE_SUBSCRIPTIONS_QUEUE")
        
        val status = when {
            result.errorsCount == 0 -> TaskExecutionStatus.SUCCESS
            result.errorsCount < result.totalProcessed -> TaskExecutionStatus.PARTIAL_SUCCESS
            else -> TaskExecutionStatus.FAILED
        }

        val message = buildString {
            append("Queues creadas: ${result.queuesGenerated} de ${result.totalProcessed} procesados")
            if (result.omittedByTvCable > 0) {
                append(", Omitidos por TV cable: ${result.omittedByTvCable}")
            }
            if (result.errorsCount > 0) {
                append(", Errores: ${result.errorsCount}")
            }
        }

        val taskLog = ScheduledTaskLog(
            taskType = ScheduledTaskType.CREATE_SUBSCRIPTIONS_QUEUE,
            executionDate = LocalDateTime.now(),
            processedCount = result.totalProcessed,
            createdCount = result.queuesGenerated,
            errorCount = result.errorsCount,
            itemsGeneratedInMikrotik = result.queuesGenerated,
            omittedByTvCable = result.omittedByTvCable,
            status = status,
            message = message,
            detailedResult = gson.toJson(result),
            errorMessage = if (result.errorsCount > 0) 
                "Se encontraron ${result.errorsCount} errores durante la creación de queues" else null
        )

        logger.info("📊 Log guardado: $message")

        return repository.save(taskLog)
    }

    fun logMonthlyBillingClose(result: MonthlyBillingCloseResultDto): ScheduledTaskLog {
        logger.info("Guardando log de ejecución: MONTHLY_BILLING_CLOSE")

        val processed = (result.massBilling.detail["processedCount"] as? Number)?.toInt() ?: 0
        val created = (result.massBilling.detail["invoicesCreated"] as? Number)?.toInt() ?: 0
        val cancelled = (result.massBilling.detail["cancelledCount"] as? Number)?.toInt() ?: 0

        val taskLog = ScheduledTaskLog(
            taskType = ScheduledTaskType.MONTHLY_BILLING_CLOSE,
            executionDate = LocalDateTime.now(),
            processedCount = processed,
            createdCount = created,
            deletedCount = cancelled,
            errorCount = listOf(
                result.subscriptionSnapshot,
                result.collectsSnapshot,
                result.massBilling,
            ).count { it.status == TaskExecutionStatus.FAILED },
            status = result.overallStatus,
            message = result.message,
            detailedResult = gson.toJson(result),
            errorMessage = result.errorMessage,
        )

        return repository.save(taskLog)
    }

    fun logTaskError(taskType: ScheduledTaskType, errorMessage: String): ScheduledTaskLog {
        logger.error("❌ Guardando log de error: $taskType - $errorMessage")
        
        val taskLog = ScheduledTaskLog(
            taskType = taskType,
            executionDate = LocalDateTime.now(),
            processedCount = 0,
            status = TaskExecutionStatus.FAILED,
            message = "Error en la ejecución de la tarea",
            errorMessage = errorMessage
        )

        return repository.save(taskLog)
    }

    fun getRecentLogs(limit: Int = 50): List<ScheduledTaskLog> {
        return repository.findRecentLogs(limit)
    }

    fun getLogsByTaskType(taskType: ScheduledTaskType): List<ScheduledTaskLog> {
        return repository.findByTaskTypeOrderByExecutionDateDesc(taskType)
    }

    fun getLogsByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): List<ScheduledTaskLog> {
        return repository.findByExecutionDateBetween(startDate, endDate)
    }
}

