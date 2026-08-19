package com.dscorp.wispadmin.wispadmin.cpe

import org.springframework.stereotype.Component

@Component
class CpeCapabilityResolver(
    private val strategies: List<CpeCapabilityStrategy>,
) {

    fun resolve(profile: CpeDeviceProfile?): CpeCapabilities {
        if (profile == null) return CpeCapabilities.FULL_TR069
        return strategies.firstOrNull { it.supports(profile) }?.capabilities(profile)
            ?: CpeCapabilities.FULL_TR069
    }
}
