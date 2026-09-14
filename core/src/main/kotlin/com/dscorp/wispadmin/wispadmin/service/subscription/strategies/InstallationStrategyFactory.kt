package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import org.springframework.stereotype.Component

@Component
class InstallationStrategyFactory(
    private val fiberStrategy: FiberInstallationStrategy,
    private val wirelessStrategy: WirelessInstallationStrategy,
    private val onlyTvStrategy: OnlyTvFiberInstallationStrategy
) {
    
    fun getStrategy(installationType: InstallationType): IInstallationStrategy {
        return when (installationType) {
            InstallationType.FIBER -> fiberStrategy
            InstallationType.WIRELESS -> wirelessStrategy
            InstallationType.ONLY_TV_FIBER -> onlyTvStrategy
        }
    }
}



