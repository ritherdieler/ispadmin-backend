package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.entity.ObsEvent
import com.dscorp.wispadmin.observability.entity.ObsIssue
import com.dscorp.wispadmin.observability.entity.ObsRumMetric
import com.dscorp.wispadmin.observability.entity.ObsSystemMetric
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class ObsLivePublisher(
    private val messagingTemplate: SimpMessagingTemplate
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun publishEvent(issue: ObsIssue, event: ObsEvent) {
        try {
            val payload = mapOf(
                "type" to "OBS_EVENT",
                "data" to mapOf(
                    "issueId" to issue.id,
                    "eventId" to event.id,
                    "fingerprint" to issue.fingerprint,
                    "title" to issue.title,
                    "platform" to event.platform,
                    "severity" to event.severity,
                    "eventType" to event.eventType,
                    "message" to (event.message?.take(300)),
                    "environment" to event.environment,
                    "release" to event.release,
                    "correlationId" to event.correlationId,
                    "url" to event.url,
                    "httpStatus" to event.httpStatus,
                    "eventCount" to issue.eventCount,
                    "createdAt" to event.createdAt?.toString()
                )
            )
            messagingTemplate.convertAndSend("/topic/observability/events", payload)
        } catch (e: Exception) {
            log.warn("No se pudo publicar evento de observabilidad en STOMP: {}", e.message)
        }
    }

    fun publishSystemMetric(metric: ObsSystemMetric) {
        try {
            val payload = mapOf(
                "type" to "OBS_SYSTEM_METRIC",
                "data" to mapOf(
                    "sampledAt" to metric.sampledAt?.toString(),
                    "heapUsedBytes" to metric.heapUsedBytes,
                    "heapMaxBytes" to metric.heapMaxBytes,
                    "nonHeapUsedBytes" to metric.nonHeapUsedBytes,
                    "cpuProcess" to metric.cpuProcess,
                    "cpuSystem" to metric.cpuSystem,
                    "osMemFreeBytes" to metric.osMemFreeBytes,
                    "osMemTotalBytes" to metric.osMemTotalBytes,
                    "threadsLive" to metric.threadsLive,
                    "threadsDaemon" to metric.threadsDaemon,
                    "threadsPeak" to metric.threadsPeak,
                    "gcCount" to metric.gcCount,
                    "gcTimeMs" to metric.gcTimeMs,
                    "poolActive" to metric.poolActive,
                    "poolIdle" to metric.poolIdle,
                    "poolWaiting" to metric.poolWaiting,
                    "poolTotal" to metric.poolTotal,
                    "poolMax" to metric.poolMax
                )
            )
            messagingTemplate.convertAndSend("/topic/observability/events", payload)
        } catch (e: Exception) {
            log.warn("No se pudo publicar metrica de sistema en STOMP: {}", e.message)
        }
    }

    fun publishRumMetric(metric: ObsRumMetric) {
        try {
            val payload = mapOf(
                "type" to "OBS_WEB_VITAL",
                "data" to mapOf(
                    "bucketStart" to metric.bucketStart?.toString(),
                    "page" to metric.page,
                    "platform" to metric.platform,
                    "metricName" to metric.metricName,
                    "sampleCount" to metric.sampleCount,
                    "p50" to metric.p50,
                    "p75" to metric.p75,
                    "p95" to metric.p95,
                    "p99" to metric.p99,
                    "min" to metric.min,
                    "max" to metric.max,
                    "avg" to metric.avg,
                    "goodCount" to metric.goodCount,
                    "needsImprovementCount" to metric.needsImprovementCount,
                    "poorCount" to metric.poorCount
                )
            )
            messagingTemplate.convertAndSend("/topic/observability/events", payload)
        } catch (e: Exception) {
            log.warn("No se pudo publicar metrica RUM en STOMP: {}", e.message)
        }
    }
}
