package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import org.springframework.stereotype.Component

@Component
class InstallationStrategyFactory(
    private val wirelessStrategy: WirelessInstallationStrategy,
    private val onlyTvStrategy: OnlyTvFiberInstallationStrategy
) {
    
    fun getStrategy(installationType: InstallationType): IInstallationStrategy {
        return when (installationType) {
            InstallationType.FIBER -> throw IllegalStateException(
                "FIBER registration uses the provisioning pipeline"
            )
            InstallationType.WIRELESS -> wirelessStrategy
            InstallationType.ONLY_TV_FIBER -> onlyTvStrategy
        }
    }
}



