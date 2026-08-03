package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.controller.toErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import org.springframework.stereotype.Component

@Component
class MikrotikPaymentReactivationHandler(
    private val errorLogRepository: ErrorLogRepository
) {

    fun reactivateFromDebtorsList(subscription: SubscriptionDto) {
        try {
            subscription.hostDevice?.executeCommand { session ->
                if (subscription.ip.isValidIpAddress()) {
                    session.print("/ip/firewall/address-list", mapOf("list" to "deudores", "address" to subscription.ip!!))
                        .forEach { addressEntry ->
                            addressEntry[".id"]?.let { id -> session.remove("/ip/firewall/address-list", id) }
                        }
                }
            }
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
        }
    }
}
