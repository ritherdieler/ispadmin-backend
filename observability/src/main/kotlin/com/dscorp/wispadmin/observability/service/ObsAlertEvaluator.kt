package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsAlertComparator
import com.dscorp.wispadmin.observability.entity.ObsAlertRule
import com.dscorp.wispadmin.observability.entity.ObsAlertType
import com.dscorp.wispadmin.observability.repository.ObsAlertRuleRepository
import com.dscorp.wispadmin.observability.repository.ObsEndpointMetricRepository
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ObsAlertEvaluator(
    private val ruleRepository: ObsAlertRuleRepository,
    private val eventRepository: ObsEventRepository,
    private val metricRepository: ObsEndpointMetricRepository,
    private val spanRepository: ObsSpanRepository,
    private val dispatcher: ObsAlertDispatcher,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val metricTypes = setOf(
        ObsAlertType.ERROR_SPIKE,
        ObsAlertType.EVENT_THRESHOLD,
        ObsAlertType.ENDPOINT_ERROR_RATE,
        ObsAlertType.ENDPOINT_LATENCY_P95,
        ObsAlertType.TRACE_ERROR_RATE
    )

    @Scheduled(
        fixedDelayString = "\${observability.alerts.evaluation-interval-ms:60000}",
        initialDelay = 30000
    )
    fun evaluate() {
        if (!properties.alerts.enabled) return
        val rules = ruleRepository.findByEnabledTrue().filter { it.type in metricTypes }
        if (rules.isEmpty()) return
        rules.forEach { rule ->
            try {
                when (rule.type) {
                    ObsAlertType.EVENT_THRESHOLD -> evaluateEventThreshold(rule)
                    ObsAlertType.ERROR_SPIKE -> evaluateErrorSpike(rule)
                    ObsAlertType.ENDPOINT_ERROR_RATE -> evaluateEndpointErrorRate(rule)
                    ObsAlertType.ENDPOINT_LATENCY_P95 -> evaluateEndpointLatency(rule)
                    ObsAlertType.TRACE_ERROR_RATE -> evaluateTraceErrorRate(rule)
                    else -> {}
                }
            } catch (e: Exception) {
                log.warn("Error evaluando regla de alerta '{}': {}", rule.name, e.message)
            }
        }
    }

    private fun windowMinutes(rule: ObsAlertRule): Long = rule.windowMinutes.coerceAtLeast(1).toLong()

    private fun evaluateEventThreshold(rule: ObsAlertRule) {
        val now = LocalDateTime.now()
        val from = now.minusMinutes(windowMinutes(rule))
        val count = eventRepository.countInWindow(from, now, rule.platform, rule.severity, rule.environment)
        if (compare(count.toDouble(), rule.threshold, rule.comparator)) {
            dispatcher.dispatch(
                rule = rule,
                title = "Umbral de eventos superado: ${rule.name}",
                message = "$count eventos en ${rule.windowMinutes} min (umbral ${rule.threshold.toLong()}).",
                dedupKey = "event-threshold:${rule.id}",
                observedValue = count.toDouble(),
                thresholdValue = rule.threshold
            )
        }
    }

    private fun evaluateErrorSpike(rule: ObsAlertRule) {
        val now = LocalDateTime.now()
        val window = windowMinutes(rule)
        val current = eventRepository.countInWindow(
            now.minusMinutes(window), now, rule.platform, rule.severity ?: "error", rule.environment
        )
        val baselineWindows = 4
        var sum = 0L
        for (i in 1..baselineWindows) {
            val to = now.minusMinutes(window * i)
            val fromB = now.minusMinutes(window * (i + 1))
            sum += eventRepository.countInWindow(fromB, to, rule.platform, rule.severity ?: "error", rule.environment)
        }
        val baseline = sum.toDouble() / baselineWindows
        val spikeThreshold = baseline * rule.baselineMultiplier
        if (current >= rule.minSample && current.toDouble() >= spikeThreshold && current > 0) {
            dispatcher.dispatch(
                rule = rule,
                title = "Pico de errores: ${rule.name}",
                message = "$current eventos en ${rule.windowMinutes} min vs baseline ${"%.1f".format(baseline)} (x${rule.baselineMultiplier}).",
                dedupKey = "spike:${rule.id}",
                observedValue = current.toDouble(),
                thresholdValue = spikeThreshold
            )
        }
    }

    private fun evaluateEndpointErrorRate(rule: ObsAlertRule) {
        val now = LocalDateTime.now()
        val from = now.minusMinutes(windowMinutes(rule))
        metricRepository.aggregateByRoute(from, now).forEach { row ->
            val route = row[0] as? String ?: return@forEach
            if (!routeMatches(rule, route)) return@forEach
            val samples = (row[2] as? Number)?.toLong() ?: 0
            val errors = (row[3] as? Number)?.toLong() ?: 0
            if (samples < rule.minSample || samples == 0L) return@forEach
            val ratePct = errors * 100.0 / samples
            if (compare(ratePct, rule.threshold, rule.comparator)) {
                dispatcher.dispatch(
                    rule = rule,
                    title = "Error rate alto en $route",
                    message = "${"%.1f".format(ratePct)}% de errores ($errors/$samples) en ${rule.windowMinutes} min (umbral ${rule.threshold}%).",
                    dedupKey = "endpoint-error:${rule.id}:$route",
                    observedValue = ratePct,
                    thresholdValue = rule.threshold
                )
            }
        }
    }

    private fun evaluateEndpointLatency(rule: ObsAlertRule) {
        val now = LocalDateTime.now()
        val from = now.minusMinutes(windowMinutes(rule))
        metricRepository.aggregateByRoute(from, now).forEach { row ->
            val route = row[0] as? String ?: return@forEach
            if (!routeMatches(rule, route)) return@forEach
            val samples = (row[2] as? Number)?.toLong() ?: 0
            val p95 = (row[5] as? Number)?.toLong() ?: 0
            if (samples < rule.minSample) return@forEach
            if (compare(p95.toDouble(), rule.threshold, rule.comparator)) {
                dispatcher.dispatch(
                    rule = rule,
                    title = "Latencia p95 alta en $route",
                    message = "p95 = ${p95} ms en ${rule.windowMinutes} min (umbral ${rule.threshold.toLong()} ms).",
                    dedupKey = "endpoint-latency:${rule.id}:$route",
                    observedValue = p95.toDouble(),
                    thresholdValue = rule.threshold
                )
            }
        }
    }

    private fun evaluateTraceErrorRate(rule: ObsAlertRule) {
        val now = LocalDateTime.now()
        val window = windowMinutes(rule)
        val fromMs = now.minusMinutes(window).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val toMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val row = spanRepository.aggregateRootSpansBetween(fromMs, toMs, rule.platform).firstOrNull() ?: return
        val total = (row[0] as? Number)?.toLong() ?: 0
        val errors = (row[1] as? Number)?.toLong() ?: 0
        if (total < rule.minSample || total == 0L) return
        val ratePct = errors * 100.0 / total
        if (compare(ratePct, rule.threshold, rule.comparator)) {
            dispatcher.dispatch(
                rule = rule,
                title = "Error rate de trazas alto: ${rule.name}",
                message = "${"%.1f".format(ratePct)}% de trazas con error ($errors/$total) en ${rule.windowMinutes} min (umbral ${rule.threshold}%).",
                dedupKey = "trace-error:${rule.id}",
                observedValue = ratePct,
                thresholdValue = rule.threshold
            )
        }
    }

    private fun routeMatches(rule: ObsAlertRule, route: String): Boolean {
        val pattern = rule.routePattern?.trim()
        if (pattern.isNullOrBlank()) return true
        return route.contains(pattern, ignoreCase = true)
    }

    private fun compare(observed: Double, threshold: Double, comparator: ObsAlertComparator): Boolean =
        when (comparator) {
            ObsAlertComparator.GT -> observed > threshold
            ObsAlertComparator.GTE -> observed >= threshold
        }
}
