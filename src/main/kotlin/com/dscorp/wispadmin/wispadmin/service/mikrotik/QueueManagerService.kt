package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.controller.toErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import me.legrange.mikrotik.ApiConnection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class QueueManagerService(
    private val subscriptionRepository: SubscriptionRepository,
    private val mikrotikService: IMikroTikService,
    private val errorLogRepository: ErrorLogRepository,
    private val scheduledTaskLogService: ScheduledTaskLogService
) : IQueueManager {
    
    private val logger = LoggerFactory.getLogger(QueueManagerService::class.java)
    
    companion object {
        private const val QUEUE_NAME_TEMPLATE_FIBER = "id:%d, usuario:%s %s, lugar:%s, nap:%s, plan:%s, tipo:%s"
        private const val QUEUE_NAME_TEMPLATE_WIRELESS = "id:%d, usuario:%s %s, lugar:%s, plan:%s, tipo:%s"
    }
    
    override fun buildQueueName(subscription: Subscription): String {
        return when (subscription.installationType) {
            InstallationType.FIBER -> {
                val napBoxCode = subscription.napBox?.code ?: ""
                QUEUE_NAME_TEMPLATE_FIBER.format(
                    subscription.id ?: 0,
                    subscription.firstName ?: "",
                    subscription.lastName ?: "",
                    subscription.place?.name ?: "",
                    napBoxCode,
                    subscription.plan?.name ?: "",
                    subscription.plan?.type ?: ""
                )
            }
            else -> {
                QUEUE_NAME_TEMPLATE_WIRELESS.format(
                    subscription.id ?: 0,
                    subscription.firstName ?: "",
                    subscription.lastName ?: "",
                    subscription.place?.name ?: "",
                    subscription.plan?.name ?: "",
                    subscription.plan?.type ?: ""
                )
            }
        }
    }
    
    override fun recreateQueueForSubscription(connection: ApiConnection, subscription: Subscription): Boolean {
        val queryByIp = "/queue/simple/print where target=${subscription.ip}/32"
        val resultByIp = connection.execute(queryByIp)

        val queueToRemove = if (resultByIp.isNotEmpty()) {
            resultByIp.last()
        } else {
            val exactName = buildQueueName(subscription)
            val queryByName = "/queue/simple/print where name='$exactName'"
            val resultByName = connection.execute(queryByName)
            resultByName.lastOrNull()
        }

        queueToRemove?.let {
            connection.execute("/queue/simple/remove numbers=${it[".id"]}")
        }

        subscription.ip?.let { ip ->
            if (ip.isNotEmpty()) {
                val queueName = buildQueueName(subscription)
                val command =
                    "/queue/simple/add name='${queueName}' target=${subscription.ip} max-limit=${subscription.plan!!.uploadSpeed!!}M/${subscription.plan!!.downloadSpeed!!}M comment='${subscription.installationType}'"
                connection.execute(command)
                return true
            }
        }
        return false
    }
    
    override fun configureMikroTikQueue(connection: ApiConnection, subscription: Subscription) {
        val queueName = buildQueueName(subscription)
        val queueCommand =
            "/queue/simple/add name='$queueName' target=${subscription.ip} max-limit=${subscription.plan?.uploadSpeed}M/${subscription.plan?.downloadSpeed}M"
        connection.execute(queueCommand)
    }
    
    override fun updateMikroTikQueue(subscription: Subscription) {
        subscription.hostDevice?.executeCommand { connection ->
            val searchQuery = "/queue/simple/print where target=${subscription.ip}/32"
            val existingQueues = connection.execute(searchQuery)
            existingQueues.forEach { queue ->
                connection.execute("/queue/simple/remove .id=${queue[".id"]}")
            }

            val queueName = buildQueueName(subscription)
            connection.execute("/queue/simple/add name='$queueName' target=${subscription.ip} max-limit=${subscription.plan?.uploadSpeed}M/${subscription.plan?.downloadSpeed}M")
        }
    }
    
    override fun createSubscriptionsSimpleQueue(): CompletableFuture<QueueCreationStats> {
        return CompletableFuture.supplyAsync {
            try {
                val allSubscriptions = subscriptionRepository.findActiveSubscriptions()
                val subscriptions = allSubscriptions.filter { it.installationType != InstallationType.ONLY_TV_FIBER }

                val totalProcessed = subscriptions.size
                val totalOmitted = allSubscriptions.size - subscriptions.size
                var queuesGenerated = 0
                var errorsCount = 0

                logger.info("🚀 Iniciando creación de queues simples - Total suscripciones activas: ${allSubscriptions.size}")

                subscriptions.firstOrNull()?.hostDevice?.let { hostDevice ->
                    hostDevice.executeCommand { connection ->
                        for (subscription in subscriptions) {
                            try {
                                val success = recreateQueueForSubscription(connection, subscription)
                                if (success) {
                                    queuesGenerated++
                                } else {
                                    errorsCount++
                                    logger.warn("⚠️ Suscripción ${subscription.id} sin IP válida")
                                }
                            } catch (e: Exception) {
                                errorsCount++
                                logger.error("❌ Error creando queue para suscripción ${subscription.id}: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    }
                }

                val stats = QueueCreationStats(
                    totalSubscriptions = allSubscriptions.size,
                    totalProcessed = totalProcessed,
                    queuesGenerated = queuesGenerated,
                    errorsCount = errorsCount,
                    omittedByTvCable = totalOmitted
                )

                logger.info("📊 Resumen de creación de queues simples:")
                logger.info("   • Total suscripciones: ${stats.totalSubscriptions}")
                logger.info("   • Registros procesados: ${stats.totalProcessed}")
                logger.info("   • Queues generados exitosamente: ${stats.queuesGenerated}")
                logger.info("   • Errores encontrados: ${stats.errorsCount}")
                logger.info("   • Omitidos por ser TV cable: ${stats.omittedByTvCable}")

                scheduledTaskLogService.logQueueCreation(stats)

                return@supplyAsync stats

            } catch (e: Exception) {
                logger.error("❌ Error general en createSubscriptionsSimpleQueue: ${e.message}")
                e.printStackTrace()

                val errorStats = QueueCreationStats(
                    totalSubscriptions = 0,
                    totalProcessed = 0,
                    queuesGenerated = 0,
                    errorsCount = 1,
                    omittedByTvCable = 0
                )
                
                scheduledTaskLogService.logQueueCreation(errorStats)

                return@supplyAsync errorStats
            }
        }
    }
}



