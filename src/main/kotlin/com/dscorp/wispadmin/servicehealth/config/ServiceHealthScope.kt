package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.HealthLabScopePort
import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.stereotype.Component

@Component
class ServiceHealthScope(
    private val properties: ServiceHealthProperties,
    private val environment: GigafiberEnvironmentProperties,
    private val subscriptions: SubscriptionRepository,
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
            subscriptions.findAllIds(),
        )
}
