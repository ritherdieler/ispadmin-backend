package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.controller.toErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.usesAddressListCut
import com.dscorp.wispadmin.wispadmin.data.model.usesPppoe
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeAccessService
import com.dscorp.wispadmin.wispadmin.util.isValidIpAddress
import org.springframework.stereotype.Component

@Component
class MikrotikPaymentReactivationHandler(
    private val errorLogRepository: ErrorLogRepository,
    private val mikrotikService: IMikroTikService,
    private val pppoeAccessService: PppoeAccessService,
) {

    fun reactivateFromDebtorsList(subscription: Subscription) {
        try {
            val device = subscription.hostDevice ?: return
            if (subscription.accessMode.usesPppoe()) {
                pppoeAccessService.restore(subscription, device)
            }
            if (subscription.accessMode.usesAddressListCut() && subscription.ip.isValidIpAddress()) {
                device.executeCommand { session ->
                    mikrotikService.removeIpFromAllCutLists(session, subscription.ip!!)
                }
            }
        } catch (e: Exception) {
            errorLogRepository.save(e.toErrorLog(Modules.PAYMENT))
        }
    }
}
