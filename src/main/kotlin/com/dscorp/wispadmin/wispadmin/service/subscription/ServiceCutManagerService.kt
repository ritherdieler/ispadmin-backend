package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.controller.toErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.CutServiceResultDto
import com.dscorp.wispadmin.wispadmin.dto.CutServiceSummaryDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.validators.ISubscriptionValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Date

@Service
class ServiceCutManagerService(
    private val subscriptionRepository: SubscriptionRepository,
    private val mikrotikService: IMikroTikService,
    private val subscriptionValidator: ISubscriptionValidator,
    private val errorLogRepository: ErrorLogRepository,
    private val scheduledTaskLogService: ScheduledTaskLogService
) : IServiceCutManager {
    
    private val logger = LoggerFactory.getLogger(ServiceCutManagerService::class.java)
    
    companion object {
        private const val DEBTORS_LIST = "deudores"
        private const val FIREWALL_DROP_RULE_COMMENT = "CORTADO POR DEUDA - LISTA DE DEUDORES"
    }
    
    @Transactional
    override fun cutInternetService(): CutServiceSummaryDto {
        val debtors = subscriptionRepository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments()
        val cancelledSubscriptions = subscriptionRepository.findCancelledSubscriptions()

        clearAddressListAndFirewallRule()
        
        val debtorsResult = processAndLogDebtors(debtors)
//        val cancelledResult = processAndLogCancelledSubscriptions(cancelledSubscriptions)
        val cancelledResult = CutServiceResultDto(
            processedCount = 0,
            createdCount = 0,
            deletedCount = 0,
            alreadyExistsCount = 0,
            errorCount = 0,
            message = "",
            debtorsCount = 0,
            cancelledCount = 0,
            omittedByTvCable = 0
        )


        createFirewallDropRule()

        val totalProcessed = debtorsResult.processedCount + cancelledResult.processedCount
        val totalCreated = debtorsResult.createdCount + cancelledResult.createdCount
        val totalAlreadyExists = debtorsResult.alreadyExistsCount + cancelledResult.alreadyExistsCount
        val totalErrors = debtorsResult.errorCount + cancelledResult.errorCount
        val totalOmittedByTvCable = debtorsResult.omittedByTvCable + cancelledResult.omittedByTvCable
        
        val allFailedItems = mutableListOf<String>()
        allFailedItems.addAll(debtorsResult.failedItems.map { "DEUDOR: $it" })
        allFailedItems.addAll(cancelledResult.failedItems.map { "CANCELADO: $it" })
        
        val allNotProcessedItems = mutableListOf<String>()
        allNotProcessedItems.addAll(debtorsResult.notProcessedItems.map { "DEUDOR: $it" })
        allNotProcessedItems.addAll(cancelledResult.notProcessedItems.map { "CANCELADO: $it" })
        
        val allAlreadyExistingItems = mutableListOf<String>()
        allAlreadyExistingItems.addAll(debtorsResult.alreadyExistingItems.map { "DEUDOR: $it" })
        allAlreadyExistingItems.addAll(cancelledResult.alreadyExistingItems.map { "CANCELADO: $it" })

        return CutServiceSummaryDto(
            debtors = debtorsResult,
            cancelled = cancelledResult,
            totalProcessed = totalProcessed,
            totalCreated = totalCreated,
            totalAlreadyExists = totalAlreadyExists,
            totalErrors = totalErrors,
            totalOmittedByTvCable = totalOmittedByTvCable,
            message = "Corte de servicio completado: $totalCreated agregados, $totalAlreadyExists ya existentes, $totalErrors errores, $totalOmittedByTvCable omitidos",
            allFailedItems = allFailedItems.toList(),
            allNotProcessedItems = allNotProcessedItems.toList(),
            allAlreadyExistingItems = allAlreadyExistingItems.toList()
        )
    }
    
    private enum class SubscriptionCutType(val prefix: String, val displayName: String) {
        DEBTOR("DEUDOR", "Deudores"),
        CANCELLED("CANCELADO", "Cancelados")
    }
    
    private fun processSubscriptionsForCut(
        subscriptions: List<Subscription>,
        cutType: SubscriptionCutType,
        isDebtorType: Boolean
    ): CutServiceResultDto {
        val allSubscriptions = subscriptions
        val subscriptionsToProcess = allSubscriptions.filter { it.installationType != InstallationType.ONLY_TV_FIBER }
        val omittedByTvCable = allSubscriptions.size - subscriptionsToProcess.size

        val subscriptionsDto = subscriptionsToProcess.map { it.toCutDto() }

        var createdCount = 0
        var alreadyExistsCount = 0
        var errorCount = 0
        val failedItems = mutableListOf<String>()
        val notProcessedItems = mutableListOf<String>()
        val alreadyExistingItems = mutableListOf<String>()

        try {
            subscriptionsDto.firstOrNull()?.hostDevice?.executeCommand { apiConnection ->
                subscriptionsDto.forEach { subscription ->
                    val validation = subscriptionValidator.validateIp(
                        subscription.ip,
                        subscription.id,
                        subscription.name,
                        cutType.prefix
                    )
                    
                    if (!validation.isValid) {
                        notProcessedItems.add(validation.errorMessage!!)
                        errorCount++
                    } else {
                        try {
                            mikrotikService.addIpToDebtorsList(apiConnection, subscription.ip!!, "${cutType.prefix}: ${subscription.name}")
                          val updatedSubscription= subscriptionRepository.findById(subscription.id!!).get().apply {
                               isServiceCutOff = true
                               lastCutOffDate = LocalDate.now()
                           }
                           val result =  subscriptionRepository.save(updatedSubscription)
                            createdCount++
                        } catch (e: Exception) {
                            if (e.message?.contains("already have such entry", ignoreCase = true) == true) {
                                alreadyExistsCount++
                                alreadyExistingItems.add("${subscription.name} (ID: ${subscription.id}, IP: ${subscription.ip})")
                                logger.info("ℹ️ ${cutType.prefix} ${subscription.id} ya existe en address list")
                            } else {
                                errorCount++
                                failedItems.add("${subscription.name} (ID: ${subscription.id}, IP: ${subscription.ip}) - ${e.message}")
                                logger.error("Error al agregar ${cutType.prefix.lowercase()} ${subscription.id}: ${e.message}")
                                errorLogRepository.save(e.toErrorLog(Modules.CUT_SERVICE))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            failedItems.add("Error general: ${e.message}")
            errorLogRepository.save(e.toErrorLog(Modules.CUT_SERVICE))
            errorCount++
        }

        val message = buildString {
            append("${cutType.displayName} procesados: $createdCount agregados")
            if (alreadyExistsCount > 0) {
                append(", Ya existentes: $alreadyExistsCount")
            }
            if (omittedByTvCable > 0) {
                append(", Omitidos por TV cable: $omittedByTvCable")
            }
            if (errorCount > 0) {
                append(", Errores: $errorCount")
            }
        }

        return CutServiceResultDto(
            processedCount = subscriptionsToProcess.size,
            createdCount = createdCount,
            deletedCount = 0,
            alreadyExistsCount = alreadyExistsCount,
            errorCount = errorCount,
            message = message,
            debtorsCount = if (isDebtorType) allSubscriptions.size else 0,
            cancelledCount = if (isDebtorType) 0 else allSubscriptions.size,
            omittedByTvCable = omittedByTvCable,
            failedItems = failedItems.toList(),
            notProcessedItems = notProcessedItems.toList(),
            alreadyExistingItems = alreadyExistingItems.toList()
        )
    }
    
    private fun processAndLogDebtors(debtors: List<Subscription>): CutServiceResultDto {
        val result = processSubscriptionsForCut(debtors, SubscriptionCutType.DEBTOR, isDebtorType = true)
        scheduledTaskLogService.logCutInternetServiceDebtors(result)
        return result
    }
    
    private fun processAndLogCancelledSubscriptions(cancelledSubscriptions: List<Subscription>): CutServiceResultDto {
        val result = processSubscriptionsForCut(cancelledSubscriptions, SubscriptionCutType.CANCELLED, isDebtorType = false)
        scheduledTaskLogService.logCutInternetServiceCancelled(result)
        return result
    }
    
    private fun clearAddressListAndFirewallRule() {
        try {
            subscriptionRepository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments()
                .firstOrNull()?.hostDevice?.executeCommand { apiConnection ->
                    val deletedCount = mikrotikService.clearAddressList(apiConnection, DEBTORS_LIST)
                    logger.info("🧹 Limpiando Address List anterior: $deletedCount entradas")

                    mikrotikService.removeFirewallRulesByComment(apiConnection, FIREWALL_DROP_RULE_COMMENT)
                    
                    logger.info("✅ Address List y reglas de firewall limpiados")
                }
        } catch (e: Exception) {
            logger.error("❌ Error al limpiar address list y firewall: ${e.message}", e)
            errorLogRepository.save(e.toErrorLog(Modules.CUT_SERVICE))
        }
    }
    
    private fun createFirewallDropRule() {
        try {
            subscriptionRepository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments()
                .firstOrNull()?.hostDevice?.executeCommand { apiConnection ->
                    mikrotikService.createFirewallDropRule(apiConnection)
                    logger.info("✅ Regla de firewall de bloqueo creada exitosamente")
                }
        } catch (e: Exception) {
            logger.error("❌ Error al crear regla de firewall: ${e.message}", e)
            errorLogRepository.save(e.toErrorLog(Modules.CUT_SERVICE))
        }
    }
}



