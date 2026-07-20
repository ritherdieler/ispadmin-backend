package com.dscorp.wispadmin.oltgateway.service

import org.springframework.stereotype.Component

@Component
class SignalCategoryCalculator {

    fun fromOnuRxDbm(rxPowerDbm: Double?): SignalCategory? {
        if (rxPowerDbm == null) return null
        return when {
            rxPowerDbm >= GOOD_THRESHOLD_DBM -> SignalCategory.GOOD
            rxPowerDbm >= WARNING_THRESHOLD_DBM -> SignalCategory.WARNING
            else -> SignalCategory.CRITICAL
        }
    }

    companion object {
        const val GOOD_THRESHOLD_DBM = -25.0
        const val WARNING_THRESHOLD_DBM = -27.0
    }
}
