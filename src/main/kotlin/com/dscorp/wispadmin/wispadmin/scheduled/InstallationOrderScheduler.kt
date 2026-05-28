//package com.dscorp.wispadmin.wispadmin.scheduled
//
//import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrderStatus
//import com.dscorp.wispadmin.wispadmin.dto.toDto
//import com.dscorp.wispadmin.wispadmin.repository.InstallationOrderRepository
//import com.dscorp.wispadmin.wispadmin.service.NotificationService
//import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
//import org.slf4j.LoggerFactory
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.scheduling.annotation.Scheduled
//import org.springframework.stereotype.Component
//
///**
// * Scheduler para tareas relacionadas con órdenes de instalación
// */
//@Component
////@EnableScheduling
//class InstallationOrderScheduler @Autowired constructor(
//    private val installationOrderRepository: InstallationOrderRepository,
//    private val notificationService: NotificationService
//) {
//    private val logger = LoggerFactory.getLogger(InstallationOrderScheduler::class.java)
//
//    private val TOPIC_INSTALLATION_ORDER = "installationOrder"
//
//    /**
//     * Comprueba cada 10 minutos si hay órdenes en estado SOLICITADO y envía notificaciones
//     * cron: segundos, minutos, horas, día del mes, mes, día de la semana
//     */
//    @Scheduled(fixedRate = 10 * 60 * 1000) // 10 minutos en milisegundos
//    fun checkPendingInstallationOrders() {
//        logger.info("Iniciando verificación de órdenes de instalación en estado SOLICITADO")
//
//        try {
//            // Obtener órdenes en estado SOLICITADO
//            val pendingOrders = installationOrderRepository.findByStatus(InstallationOrderStatus.SOLICITADO)
//
//            if (pendingOrders.isEmpty()) {
//                logger.info("No hay órdenes de instalación en estado SOLICITADO")
//                return
//            }
//
//            logger.info("Se encontraron ${pendingOrders.size} órdenes en estado SOLICITADO")
//
//            // Enviar una única notificación si hay solo una orden pendiente
//            if (pendingOrders.size == 1) {
//                val order = pendingOrders.first()
//                notificationService.sendTopicNotification(
//                    topic = TOPIC_INSTALLATION_ORDER,
//                    title = "Recordatorio: Orden de instalación pendiente",
//                    message = "La orden para ${order.customerFirstName} ${order.customerLastName} sigue en estado SOLICITADO y requiere asignación",
//                    data = order.toDto(),
//                    type = FcmMessage.FcmMessageType.INSTALLATION_ORDER,
//                    id = order.id.toString()
//                )
//
//                logger.info("Enviada notificación para la orden #${order.id}")
//            } else {
//                // Si hay más de una orden, enviar una única notificación general
//                notificationService.sendTopicNotification(
//                    topic = TOPIC_INSTALLATION_ORDER,
//                    title = "Órdenes pendientes de instalación",
//                    message = "Hay un total de ${pendingOrders.size} órdenes de instalación que requieren asignación",
//                    data = null,
//                    type = FcmMessage.FcmMessageType.INSTALLATION_ORDER,
//                    id = "summary_pending_orders"
//                )
//
//                logger.info("Enviada notificación general para ${pendingOrders.size} órdenes pendientes")
//            }
//        } catch (e: Exception) {
//            logger.error("Error al verificar órdenes pendientes", e)
//        }
//    }
//}