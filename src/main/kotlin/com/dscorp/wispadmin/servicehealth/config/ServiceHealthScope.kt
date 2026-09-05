package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.servicehealth.port.HealthLabScopePort
import org.springframework.stereotype.Component

@Component
class ServiceHealthScope(
    private val properties: ServiceHealthProperties,
    private val environment: GigafiberEnvironmentProperties,
    private val subscriptions: SubscriptionRepository,
) : HealthLabScopePort {
    fun environmentTag(): String = environment.normalizedTag()

    fun lab(id: Int?): Boolean = id != null && id in properties.labSubscriptionIds

    override fun collects(subscriptionId: Int?): Boolean =
        properties.collects(subscriptionId, lab(subscriptionId), environmentTag())

    override fun collectionSubscriptionIds(): Set<Int> =
        properties.collectionSubscriptionIds(
            properties.labSubscriptionIds,
            environmentTag(),
            subscriptions.findAllIds(),
        )
}
