package com.dscorp.wispadmin.wispadmin.cpe

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Fallback for CPEs with no specific rule: assume the ACS tree is authoritative. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class DefaultCpeCapabilityStrategy : CpeCapabilityStrategy {

    override fun supports(profile: CpeDeviceProfile): Boolean = true

    override fun capabilities(profile: CpeDeviceProfile): CpeCapabilities = CpeCapabilities.FULL_TR069
}
