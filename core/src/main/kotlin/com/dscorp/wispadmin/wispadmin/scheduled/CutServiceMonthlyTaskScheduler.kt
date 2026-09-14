package com.dscorp.wispadmin.wispadmin.scheduled

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class CutServiceMonthlyTaskScheduler(
    private val subscriptionService: SubscriptionService,
    private val scheduledTaskLogService: ScheduledTaskLogService,
    private val clock: Clock = Clock.system(ZoneId.of("America/Lima")),
) {

    private val logger = LoggerFactory.getLogger(CutServiceMonthlyTaskScheduler::class.java)
    private val zone: ZoneId = ZoneId.of("America/Lima")

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Lima")
    fun executeIfCutDay() {
        val today = LocalDate.now(clock.withZone(zone))
        if (!ServiceCutSchedule.shouldRun(today)) {
            return
        }
        try {
            logger.info("Iniciando tarea programada: Corte de servicio mensual - ${LocalDateTime.now(clock)}")
            subscriptionService.cutInternetService()
            logger.info("Tarea completada exitosamente")
        } catch (e: Exception) {
            logger.error("Error en tarea programada de corte de servicio: ${e.message}", e)
            scheduledTaskLogService.logTaskError(
                ScheduledTaskType.CUT_INTERNET_SERVICE,
                e.message ?: "Error desconocido"
            )
        }
    }
}
