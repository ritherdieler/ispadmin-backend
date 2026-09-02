package com.dscorp.wispadmin.traffic.adapter

import com.dscorp.wispadmin.servicehealth.port.HealthTrafficAnomaly
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficRun
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficSample
import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAnomalyEventRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class HealthTrafficAdapter(
    private val samples: SubscriptionTrafficSampleRepository,
    private val runs: TrafficSourceRunRepository,
    private val anomalies: TrafficAnomalyEventRepository,
    private val properties: TrafficProperties
) : HealthTrafficPort {

    override fun latestSample(subscriptionId: Int): HealthTrafficSample? {
        val sample = samples.findTopBySubscriptionIdOrderByBucketStartDesc(subscriptionId) ?: return null
        return HealthTrafficSample(
            id = sample.id,
            hostDeviceId = sample.hostDeviceId,
            collectedAt = sample.collectedAt,
            sampleStatus = sample.sampleStatus.name,
            avgMbpsDown = sample.avgMbpsDown,
            avgMbpsUp = sample.avgMbpsUp,
            queueId = sample.queueId
        )
    }

    override fun latestRun(hostDeviceId: Int): HealthTrafficRun? {
        val run = runs.findTopByHostDeviceIdOrderByStartedAtDesc(hostDeviceId) ?: return null
        return HealthTrafficRun(
            id = run.id,
            completedAt = run.completedAt,
            status = run.status.name
        )
    }

    override fun findAnomalyChanges(after: LocalDateTime, afterId: Long, page: Pageable): List<HealthTrafficAnomaly> {
        return anomalies.findChanges(after, afterId, page).mapNotNull { event ->
            val id = event.id ?: return@mapNotNull null
            HealthTrafficAnomaly(
                id = id,
                subscriptionId = event.subscriptionId,
                hostDeviceId = event.hostDeviceId,
                eventStatus = event.eventStatus.name,
                anomalyType = event.anomalyType.name,
                lastEvaluatedAt = event.lastEvaluatedAt,
                coveragePct = event.coveragePct,
                confidence = event.confidence,
                evidenceJson = event.evidenceJson
            )
        }
    }

    override fun minimumCoveragePct(): Double = properties.anomaly.minimumCoveragePct
}
