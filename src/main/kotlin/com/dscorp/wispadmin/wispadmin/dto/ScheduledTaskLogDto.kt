package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskLog
import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.data.model.TaskExecutionStatus
import java.time.LocalDateTime

data class ScheduledTaskLogDto(
    val id: Int?,
    val taskType: String,
    val taskTypeName: String,   
    val executionDate: LocalDateTime,
    val processedCount: Int,
    val createdCount: Int,
    val deletedCount: Int,
    val alreadyExistsCount: Int,
    val errorCount: Int,
    val itemsGeneratedInMikrotik: Int,
    val omittedByTvCable: Int,
    val status: String,
    val statusName: String,
    val message: String?,
    val errorMessage: String?
)

fun ScheduledTaskLog.toDto(): ScheduledTaskLogDto {
    return ScheduledTaskLogDto(
        id = this.id,
        taskType = this.taskType.name,
        taskTypeName = getTaskTypeName(this.taskType),
        executionDate = this.executionDate,
        processedCount = this.processedCount,
        createdCount = this.createdCount,
        deletedCount = this.deletedCount,
        alreadyExistsCount = this.alreadyExistsCount,
        errorCount = this.errorCount,
        itemsGeneratedInMikrotik = this.itemsGeneratedInMikrotik,
        omittedByTvCable = this.omittedByTvCable,
        status = this.status.name,
        statusName = getStatusName(this.status),
        message = this.message,
        errorMessage = this.errorMessage
    )
}

private fun getTaskTypeName(taskType: ScheduledTaskType): String {
    return when (taskType) {
        ScheduledTaskType.CUT_INTERNET_SERVICE -> "Corte de Servicio de Internet"
        ScheduledTaskType.CUT_INTERNET_SERVICE_DEBTORS -> "Corte de Servicio - Deudores"
        ScheduledTaskType.CUT_INTERNET_SERVICE_CANCELLED -> "Corte de Servicio - Cancelados"
        ScheduledTaskType.GENERATE_ADDRESS_LIST_CANCELLED -> "Generación de Address List (Cancelados)"
        ScheduledTaskType.CREATE_SUBSCRIPTIONS_QUEUE -> "Creación de Queues de Suscripciones"
        ScheduledTaskType.MONTHLY_BILLING_CLOSE -> "Cierre mensual (resumen)"
        ScheduledTaskType.MONTHLY_CLOSE_SUBSCRIPTION_SNAPSHOT -> "Cierre mensual - snapshot suscripciones"
        ScheduledTaskType.MONTHLY_CLOSE_COLLECTS_SNAPSHOT -> "Cierre mensual - snapshot recaudación"
        ScheduledTaskType.MONTHLY_CLOSE_MASS_BILLING -> "Cierre mensual - facturación masiva"
    }
}

private fun getStatusName(status: TaskExecutionStatus): String {
    return when (status) {
        TaskExecutionStatus.SUCCESS -> "Exitoso"
        TaskExecutionStatus.PARTIAL_SUCCESS -> "Parcialmente Exitoso"
        TaskExecutionStatus.FAILED -> "Fallido"
    }
}

