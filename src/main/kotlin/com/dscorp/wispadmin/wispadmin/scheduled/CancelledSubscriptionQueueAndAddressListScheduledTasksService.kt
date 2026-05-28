//package com.dscorp.wispadmin.wispadmin.scheduled
//
//import com.dscorp.wispadmin.wispadmin.service.SubscriptionService
//import org.slf4j.LoggerFactory
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.scheduling.annotation.Scheduled
//import org.springframework.stereotype.Service
//import java.time.LocalDateTime
//
//@Service
//class CancelledSubscriptionQueueAndAddressListScheduledTasksService {
//
//    private val logger = LoggerFactory.getLogger(CancelledSubscriptionQueueAndAddressListScheduledTasksService::class.java)
//
//    @Autowired
//    private lateinit var subscriptionService: SubscriptionService
//
//    /**
//     * Ejecuta la generación de Address List y Queue para suscripciones canceladas
//     * todos los días 1 de cada mes a la 1:00 AM
//     */
//    @Scheduled(cron = "0 0 1 1 * *")
//    fun executeMonthlyAddressListAndQueueGeneration() {
//        try {
//            logger.info("🔄 Iniciando tarea programada: Generación de Address List y Queue - ${LocalDateTime.now()}")
//
//            // Ejecutar generación de Address List para suscripciones canceladas
//            logger.info("📋 FASE 1: Generando Address List para suscripciones canceladas...")
//            val addressListResult = subscriptionService.generateAddressListForCancelledSubscriptions()
//            logger.info("✅ Address List generada: ${addressListResult.message}")
//            logger.info("📊 Address List - Creadas: ${addressListResult.createdCount}, Ya existían: ${addressListResult.alreadyExistsCount}, Errores: ${addressListResult.errorCount}")
//
//            if (addressListResult.notProcessedSubscriptions.isNotEmpty()) {
//                logger.warn("⚠️ Address List - Suscripciones no procesadas: ${addressListResult.notProcessedSubscriptions.size}")
//            }
//
//            if (addressListResult.failedSubscriptions.isNotEmpty()) {
//                logger.error("❌ Address List - Suscripciones fallidas: ${addressListResult.failedSubscriptions.size}")
//            }
//
//            // Ejecutar creación de Queue para todas las suscripciones
//            logger.info("📋 FASE 2: Creando Queue para todas las suscripciones...")
//            val queueFuture = subscriptionService.createSubscriptionsSimpleQueue()
//            val queueResult = queueFuture.get()
//            logger.info("✅ Queue creada: Procesadas: ${queueResult.totalProcessed}, Creadas: ${queueResult.queuesGenerated}, Errores: ${queueResult.errorsCount}")
//
//            logger.info("🎉 Tarea programada completada exitosamente - ${LocalDateTime.now()}")
//
//        } catch (e: Exception) {
//            logger.error("❌ Error en tarea programada: ${e.message}", e)
//        }
//    }
//
//}