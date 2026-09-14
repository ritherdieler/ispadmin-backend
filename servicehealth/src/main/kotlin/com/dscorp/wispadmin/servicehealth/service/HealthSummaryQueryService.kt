package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.events.HealthSnapshotCache
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.HealthCurrent
import com.dscorp.wispadmin.servicehealth.dto.ActionPolicy
import com.dscorp.wispadmin.servicehealth.dto.HealthSummary
import com.dscorp.wispadmin.servicehealth.repository.HealthCurrentRepository
import com.dscorp.wispadmin.servicehealth.repository.HealthEventRepository
import com.dscorp.wispadmin.servicehealth.repository.RemoteActionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class HealthSummaryQueryService(
    private val reader: HealthEvidenceReader,
    private val engine: DiagnosisEngine,
    private val current: HealthCurrentRepository,
    private val cache: ObjectProvider<HealthSnapshotCache>,
    private val events: HealthEventRepository,
    private val actions: RemoteActionRepository,
    private val subscriptionContext: ServiceHealthSubscriptionContextReader,
    private val properties: ServiceHealthProperties,
    private val json: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(HealthSummaryQueryService::class.java)
    fun summary(id: Int, now: Instant = Instant.now()): HealthSummary {
        cache.ifUnique?.getSummaryJson(id)?.let { cached ->
            parseSummary(cached)?.let { return decorate(id, it) }
        }
        val row = current.findById(id).orElse(null)
        if (row != null && isFresh(row.evaluatedAt, now)) {
            parseSummary(row.summaryJson)?.let { stored ->
                cache.ifUnique?.putSummaryJson(id, row.summaryJson, Duration.ofSeconds(properties.snapshotFreshSeconds.coerceAtLeast(1)))
                return decorate(id, stored)
            }
        }
        val evaluated = engine.evaluate(reader.read(id, now))
        persist(id, evaluated, now)
        return decorate(id, evaluated)
    }

    fun reevaluate(id: Int, now: Instant = Instant.now()): HealthSummary {
        val evaluated = engine.evaluate(reader.read(id, now))
        persist(id, evaluated, now)
        return decorate(id, evaluated)
    }

    private fun persist(id: Int, summary: HealthSummary, now: Instant) {
        val payload = json.writeValueAsString(summary)
        current.save(HealthCurrent(subscriptionId = id, evaluatedAt = now, summaryJson = payload))
        cache.ifUnique?.putSummaryJson(id, payload, Duration.ofSeconds(properties.snapshotFreshSeconds.coerceAtLeast(1)))
    }

    private fun isFresh(evaluatedAt: Instant, now: Instant): Boolean {
        val window = properties.snapshotFreshSeconds.coerceAtLeast(1)
        return !evaluatedAt.isBefore(now.minusSeconds(window))
    }

    private fun parseSummary(payload: String): HealthSummary? {
        return try {
            json.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(payload, HealthSummary::class.java)
        } catch (ex: Exception) {
            logger.warn("Could not parse service_health_current json: {}", ex.message)
            null
        }
    }

    private fun decorate(id: Int, summary: HealthSummary): HealthSummary {
        val context = subscriptionContext.read(id)
        val open = events.findBySubscriptionIdAndEventStatus(id, "OPEN")
        return summary.copy(
            actionPolicy = actionPolicy(summary),
            subscriber = context.subscriber,
            serviceContext = context.serviceContext,
            diagnoses = summary.diagnoses.map { d ->
                d.copy(suppressingIncidentId = open.firstOrNull { it.diagnosisCode == d.diagnosisCode }?.suppressingIncidentId)
            },
        )
    }

    private fun actionPolicy(summary: HealthSummary): Map<String, ActionPolicy> {
        val sn = summary.identity["ONU"] as? String
        fun available(enabled: Boolean, reason: String?, lastActionAt: Instant?): ActionPolicy =
            ActionPolicy(enabled, if (!enabled) reason else null, lastActionAt, null)
        val deviceKey = sn?.let { "ONU:${it.uppercase()}" }
        val lastWifi = deviceKey?.let {
            actions.findTopByDeviceKeyAndActionInOrderByCreatedAtDesc(it, listOf("WIFI_REFRESH", "CONFIG", "REBOOT_ACS"))
        }?.createdAt
        val lastOptical = deviceKey?.let {
            actions.findTopByDeviceKeyAndActionInOrderByCreatedAtDesc(it, listOf("OPTICAL_REFRESH"))
        }?.createdAt
        return mapOf(
            "wifi" to available(
                summary.actionsEnabled && summary.identity["ACS"] != null,
                "Acción Wi-Fi no disponible: falta habilitación o vínculo ACS",
                lastWifi,
            ),
            "optical" to available(
                summary.actionsEnabled && properties.opticalEnabled && sn != null,
                "Acción óptica no disponible: falta habilitación o vínculo ONU",
                lastOptical,
            ),
        )
    }
}
