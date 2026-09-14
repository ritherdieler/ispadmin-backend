package com.dscorp.wispadmin.wispadmin.service.validators

import com.dscorp.wispadmin.wispadmin.controller.toErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.subscription.SubscriptionVlanRules
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SubscriptionValidatorService(
    private val errorLogRepository: ErrorLogRepository
) : ISubscriptionValidator {
    
    private val logger = LoggerFactory.getLogger(SubscriptionValidatorService::class.java)
    
    override fun validateIp(
        ip: String?,
        subscriptionId: Int?,
        subscriptionName: String,
        prefix: String
    ): ValidationResult {
        return when {
            ip.isNullOrEmpty() -> {
                val message = "$prefix sin IP asignada - ID: $subscriptionId, Nombre: $subscriptionName"
                logger.warn("⚠️ $message")
                errorLogRepository.save(
                    Exception(message).toErrorLog(Modules.CUT_SERVICE)
                )
                ValidationResult(
                    isValid = false,
                    errorMessage = "$subscriptionName (ID: $subscriptionId) - Sin IP asignada"
                )
            }
            !ip.isValidIpAddress() -> {
                val logMessage = "Invalid IP address: $ip - ID: $subscriptionId, Nombre: $subscriptionName"
                logger.error(logMessage)
                errorLogRepository.save(
                    Exception("Invalid IP address: $ip").toErrorLog(Modules.CUT_SERVICE)
                )
                ValidationResult(
                    isValid = false,
                    errorMessage = "$subscriptionName (ID: $subscriptionId) - IP inválida: $ip"
                )
            }
            else -> ValidationResult(isValid = true)
        }
    }
    
    override fun validateSubscriptionRequest(request: SubscriptionRequest) {
        require(request.firstName.isNotBlank()) { "El nombre es requerido" }
        require(request.lastName.isNotBlank()) { "El apellido es requerido" }
        require(request.planId > 0) { "El plan es requerido" }
        require(request.hostDeviceId > 0) { "El dispositivo host es requerido" }
        require(request.placeId > 0) { "El lugar es requerido" }

        if (request.installationType == InstallationType.FIBER) {
            require(request.onu != null) { "La ONU es requerida para instalación de fibra" }
            require(request.napBoxId != null) { "El NAP Box es requerido para instalación de fibra" }
            SubscriptionVlanRules.requireAppVlan(request.vlan)
        }
    }
}

