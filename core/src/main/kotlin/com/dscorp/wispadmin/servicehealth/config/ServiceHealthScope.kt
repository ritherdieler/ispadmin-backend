package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.HealthLabScopePort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import org.springframework.stereotype.Component

@Component
class ServiceHealthScope(
    private val directory: SubscriptionDirectoryPort,
    private val acs: AcsSubscriptionPort,
) : HealthLabScopePort {
    fun lab(id: Int?): Boolean = acs.isLab(id)

    override fun collectionSubscriptionIds(): Set<Int> = directory.allIds().toSet()
}
