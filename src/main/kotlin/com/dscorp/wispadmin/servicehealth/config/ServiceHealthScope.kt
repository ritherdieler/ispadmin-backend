package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import org.springframework.stereotype.Component

@Component
class ServiceHealthScope(
    private val properties: ServiceHealthProperties,
    private val environment: GigafiberEnvironmentProperties,
    private val acs: SubscriptionAcsRepository,
) {
    fun environmentTag(): String = environment.normalizedTag()

    fun lab(id: Int?): Boolean = id != null && acs.findById(id).orElse(null)?.lab == true

    fun collects(id: Int?): Boolean = properties.collects(id, lab(id), environmentTag())

    fun collectionSubscriptionIds(): Set<Int> =
        properties.collectionSubscriptionIds(acs.findByLabIsTrue().map { it.subscriptionId }, environmentTag())
}
