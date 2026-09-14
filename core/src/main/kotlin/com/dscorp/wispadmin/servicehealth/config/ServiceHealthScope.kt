package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.HealthLabScopePort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import org.springframework.stereotype.Component

@Component
class ServiceHealthScope(
    private val properties: ServiceHealthProperties,
    private val environment: GigafiberEnvironmentProperties,
    private val directory: SubscriptionDirectoryPort,
    private val acs: AcsSubscriptionPort,
) : HealthLabScopePort {
    fun environmentTag(): String = environment.normalizedTag()

    fun lab(id: Int?): Boolean = acs.isLab(id)

    override fun collects(subscriptionId: Int?): Boolean =
        properties.collects(subscriptionId, lab(subscriptionId), environmentTag())

    override fun collectionSubscriptionIds(): Set<Int> =
        properties.collectionSubscriptionIds(
            acs.labSubscriptionIds(),
            environmentTag(),
            directory.allIds(),
        )
}
