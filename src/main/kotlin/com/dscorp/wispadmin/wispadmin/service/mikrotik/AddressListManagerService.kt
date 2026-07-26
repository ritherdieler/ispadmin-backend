package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.AddressListGenerationResultDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AddressListManagerService(
    private val subscriptionRepository: SubscriptionRepository,
    private val mikrotikService: IMikroTikService,
    private val scheduledTaskLogService: ScheduledTaskLogService
) : IAddressListManager {
    
    private val logger = LoggerFactory.getLogger(AddressListManagerService::class.java)
    
    companion object {
        private const val DEBTORS_LIST = "deudores"
    }
    
    override fun generateAddressListForCancelledSubscriptions(): AddressListGenerationResultDto {
        return try {
            val cancelledSubscriptions = subscriptionRepository.findByServiceStatus(ServiceStatus.CANCELLED)
                .filter { it.installationType != InstallationType.ONLY_TV_FIBER }

            val result = AddressListGenerationResult()
            
            val subscriptionsByDevice = groupSubscriptionsByDevice(cancelledSubscriptions, result)
            
            clearExistingAddressLists(subscriptionsByDevice, result)
            createAddressLists(subscriptionsByDevice, result)
            
            buildResult(result)
        } catch (e: Exception) {
            throw Exception("Error al generar Filter Rules para suscripciones canceladas: ${e.message}")
        }
    }
    
    private data class AddressListGenerationResult(
        var processedCount: Int = 0,
        var successCount: Int = 0,
        var errorCount: Int = 0,
        var deletedCount: Int = 0,
        var alreadyExistsCount: Int = 0,
        val notProcessedSubscriptions: MutableList<String> = mutableListOf(),
        val failedSubscriptions: MutableList<String> = mutableListOf(),
        val alreadyExistsSubscriptions: MutableList<String> = mutableListOf(),
        val createdSubscriptions: MutableList<String> = mutableListOf()
    )
    
    private fun groupSubscriptionsByDevice(
        subscriptions: List<Subscription>,
        result: AddressListGenerationResult
    ): Map<NetworkDevice, List<Subscription>> {
        val validSubscriptions = subscriptions.filter {
            it.hostDevice != null && it.ip?.isValidIpAddress() == true
        }

        val invalidSubscriptions = subscriptions.filter {
            it.hostDevice == null || it.ip?.isValidIpAddress() != true
        }

        invalidSubscriptions.forEach { subscription ->
            val reason = when {
                subscription.hostDevice == null -> "Sin dispositivo host"
                subscription.ip?.isValidIpAddress() != true -> "IP inválida: ${subscription.ip}"
                else -> "Razón desconocida"
            }
            result.notProcessedSubscriptions.add(
                "${subscription.getFullName()} (ID: ${subscription.id}) - $reason"
            )
        }

        return validSubscriptions.groupBy { it.hostDevice!! }
    }
    
    private fun clearExistingAddressLists(
        subscriptionsByDevice: Map<NetworkDevice, List<Subscription>>,
        result: AddressListGenerationResult
    ) {
        subscriptionsByDevice.forEach { (device, subscriptions) ->
            try {
                device.executeCommand { session ->
                    clearAddressList(session, result)
                    clearFirewallRules(session, result)
                }
            } catch (e: Exception) {
                result.errorCount += subscriptions.size
            }
        }
    }
    
    private fun clearAddressList(session: MikrotikSession, result: AddressListGenerationResult) {
        val deletedCount = mikrotikService.clearAddressList(session, DEBTORS_LIST)
        result.deletedCount += deletedCount
    }
    
    private fun clearFirewallRules(session: MikrotikSession, result: AddressListGenerationResult) {
        val deletedCount = mikrotikService.clearFirewallRules(session)
        result.deletedCount += deletedCount
    }
    
    private fun createAddressLists(
        subscriptionsByDevice: Map<NetworkDevice, List<Subscription>>,
        result: AddressListGenerationResult
    ) {
        subscriptionsByDevice.forEach { (device, subscriptions) ->
            try {
                device.executeCommand { session ->
                    addSubscriptionsToAddressList(subscriptions, session, result)
                    createFirewallRule(session)
                }
            } catch (e: Exception) {
                result.errorCount += subscriptions.size
                subscriptions.forEach { subscription ->
                    result.failedSubscriptions.add(
                        "${subscription.getFullName()} (ID: ${subscription.id}) - Error de conexión al dispositivo: ${e.message}"
                    )
                }
            }
        }
    }
    
    private fun addSubscriptionsToAddressList(
        subscriptions: List<Subscription>,
        session: MikrotikSession,
        result: AddressListGenerationResult
    ) {
        subscriptions.forEach { subscription ->
            try {
                val existingAddress = session.execute(
                    "/ip/firewall/address-list/print where list=$DEBTORS_LIST and address=${subscription.ip}"
                )

                if (existingAddress.isEmpty()) {
                    session.execute(
                        "/ip/firewall/address-list/add list=$DEBTORS_LIST address=${subscription.ip} comment='${
                            subscription.getFullName().uppercase()
                        }'"
                    )
                    result.successCount++
                    result.createdSubscriptions.add(
                        "${subscription.getFullName()} (ID: ${subscription.id}) - IP: ${subscription.ip}"
                    )
                } else {
                    result.alreadyExistsCount++
                    result.alreadyExistsSubscriptions.add(
                        "${subscription.getFullName()} (ID: ${subscription.id}) - IP: ${subscription.ip}"
                    )
                }
                result.processedCount++
            } catch (e: Exception) {
                result.errorCount++
                result.failedSubscriptions.add(
                    "${subscription.getFullName()} (ID: ${subscription.id}) - Error: ${e.message}"
                )
            }
        }
    }
    
    private fun createFirewallRule(session: MikrotikSession) {
        try {
            mikrotikService.createFirewallDropRule(session)
        } catch (e: Exception) {
            logger.warn("Error creando regla de firewall: ${e.message}")
        }
    }
    
    private fun buildResult(result: AddressListGenerationResult): AddressListGenerationResultDto {
        val totalNotCreated = result.notProcessedSubscriptions.size + result.failedSubscriptions.size
        val message = "Procesadas: ${result.processedCount}, Creadas: ${result.successCount}, " +
                "Ya existían: ${result.alreadyExistsCount}, Eliminadas: ${result.deletedCount}, " +
                "Errores: ${result.errorCount}"

        val dto = AddressListGenerationResultDto(
            processedCount = result.processedCount,
            createdCount = result.successCount,
            alreadyExistsCount = result.alreadyExistsCount,
            deletedCount = result.deletedCount,
            errorCount = result.errorCount,
            totalNotCreated = totalNotCreated,
            message = message,
            createdSubscriptions = result.createdSubscriptions.toList(),
            alreadyExistsSubscriptions = result.alreadyExistsSubscriptions.toList(),
            notProcessedSubscriptions = result.notProcessedSubscriptions.toList(),
            failedSubscriptions = result.failedSubscriptions.toList()
        )
        
        scheduledTaskLogService.logAddressListGeneration(dto)
        
        return dto
    }
}

