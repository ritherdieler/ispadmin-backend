package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.client.HealthOltGatewayHttpClient
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.port.HealthOltIngestPort
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class HealthOltOpticalPullService(
    private val properties: ServiceHealthProperties,
    private val gateway: HealthOltGatewayHttpClient,
    private val ingestProvider: ObjectProvider<HealthOltIngestPort>,
) {
    @Scheduled(
        fixedDelayString = "\${service.health.optical-pull-interval-ms:300000}",
        initialDelayString = "\${service.health.optical-pull-initial-delay-ms:90000}",
    )
    fun pull() {
        if (!properties.enabled || !properties.opticalEnabled) return
        val ingest = ingestProvider.ifAvailable ?: return
        val now = Instant.now()
        gateway.pullOptical().forEach { ingest.onOptical(it) }
        gateway.pullStates().forEach { (sn, state, cause) ->
            ingest.onState(sn, state, cause, now)
        }
    }
}
