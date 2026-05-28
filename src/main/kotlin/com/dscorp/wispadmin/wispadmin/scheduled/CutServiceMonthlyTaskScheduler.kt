package com.dscorp.wispadmin.wispadmin.scheduled

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CutServiceMonthlyTaskScheduler {

    private val logger = LoggerFactory.getLogger(CutServiceMonthlyTaskScheduler::class.java)

    @Autowired
    lateinit var subscriptionService: SubscriptionService

    @Autowired
    lateinit var scheduledTaskLogService: ScheduledTaskLogService

    @Scheduled(cron = "0 0 0 16 * MON-FRI")
    fun executeOn16thIfWeekday() {
        try {
            logger.info("🔄 Iniciando tarea programada: Corte de servicio mensual - ${LocalDateTime.now()}")
            subscriptionService.cutInternetService()
            logger.info("✅ Tarea completada exitosamente - Se generaron 2 logs (deudores y cancelados)")
        } catch (e: Exception) {
            logger.error("❌ Error en tarea programada de corte de servicio: ${e.message}", e)
            scheduledTaskLogService.logTaskError(
                ScheduledTaskType.CUT_INTERNET_SERVICE,
                e.message ?: "Error desconocido"
            )
        }
    }

    @Scheduled(cron = "0 0 0 18-23 * TUE")
    fun executeOnTuesdayAfterWeekend16th() {
        val today = LocalDate.now()
        val sixteenth = today.withDayOfMonth(16)
        val dayOfWeek = sixteenth.dayOfWeek

        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            try {
                logger.info("🔄 Iniciando tarea programada (martes después de fin de semana): Corte de servicio mensual - ${LocalDateTime.now()}")
                subscriptionService.cutInternetService()
                logger.info("✅ Tarea completada exitosamente - Se generaron 2 logs (deudores y cancelados)")
            } catch (e: Exception) {
                logger.error("❌ Error en tarea programada de corte de servicio (martes): ${e.message}", e)
                scheduledTaskLogService.logTaskError(
                    ScheduledTaskType.CUT_INTERNET_SERVICE,
                    e.message ?: "Error desconocido"
                )
            }
        }
    }

}