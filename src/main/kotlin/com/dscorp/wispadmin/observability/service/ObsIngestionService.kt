package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsEvent
import com.dscorp.wispadmin.observability.entity.ObsIssue
import com.dscorp.wispadmin.observability.entity.ObsIssueStatus
import com.dscorp.wispadmin.observability.port.ReportedEvent
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class ObsIngestionService(
    private val issueRepository: ObsIssueRepository,
    private val eventRepository: ObsEventRepository,
    private val fingerprintService: ObsFingerprintService,
    private val livePublisher: ObsLivePublisher,
    private val issueAlertHandler: ObsIssueAlertHandler,
    private val properties: ObservabilityProperties,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val rateWindow = ConcurrentHashMap<String, RateBucket>()

    fun allowRequest(bucketKey: String): Boolean {
        val limit = properties.rateLimitPerMinute
        if (limit <= 0) return true
        val minute = System.currentTimeMillis() / 60000
        val bucket = rateWindow.compute(bucketKey) { _, existing ->
            if (existing == null || existing.minute != minute) RateBucket(minute, AtomicLong(0))
            else existing
        }!!
        return bucket.count.incrementAndGet() <= limit
    }

    @Async("obsTaskExecutor")
    fun ingestAsync(event: ReportedEvent) {
        try {
            persistEvent(event)
        } catch (e: Exception) {
            log.warn("Fallo al ingerir evento de observabilidad async: {}", e.message)
        }
    }

    @Transactional
    fun persistEvent(event: ReportedEvent): Long? {
        val platform = event.platform.ifBlank { "unknown" }
        val severity = event.severity.ifBlank { "error" }
        val fingerprint = fingerprintService.fingerprint(platform, event.errorType, event.stacktrace, event.message)
        val now = LocalDateTime.now()
        val occurredAt = event.timestamp?.let { toLocalDateTime(it) } ?: now

        val existingIssue = issueRepository.findByFingerprint(fingerprint)
        val isNewIssue = existingIssue == null
        val wasReopened = existingIssue?.status == ObsIssueStatus.RESOLVED

        val issue = existingIssue?.also {
            it.eventCount += 1
            it.lastSeen = occurredAt
            it.severity = severity
            it.lastMessage = event.message?.take(4000)
            it.lastEnvironment = event.environment
            it.lastRelease = event.release
            if (it.status == ObsIssueStatus.RESOLVED) it.status = ObsIssueStatus.OPEN
        } ?: ObsIssue(
            fingerprint = fingerprint,
            title = buildTitle(event),
            platform = platform,
            severity = severity,
            status = ObsIssueStatus.OPEN,
            errorType = event.errorType?.take(300),
            lastMessage = event.message?.take(4000),
            lastEnvironment = event.environment,
            lastRelease = event.release,
            eventCount = 1,
            firstSeen = occurredAt,
            lastSeen = occurredAt
        )

        val savedIssue = issueRepository.save(issue)

        val obsEvent = ObsEvent(
            issueId = savedIssue.id,
            fingerprint = fingerprint,
            eventType = event.eventType.ifBlank { "error" },
            platform = platform,
            severity = severity,
            feature = tagString(event.tags, "feature")?.take(80),
            action = tagString(event.tags, "action")?.take(120),
            message = event.message,
            errorType = event.errorType?.take(300),
            stacktrace = event.stacktrace,
            environment = event.environment,
            release = event.release,
            correlationId = event.correlationId,
            sessionId = event.sessionId,
            url = event.url,
            httpMethod = event.httpMethod,
            httpStatus = event.httpStatus,
            durationMs = event.durationMs,
            userAgent = event.userAgent,
            userJson = toJson(event.user),
            deviceJson = toJson(event.device),
            breadcrumbsJson = toJson(event.breadcrumbs),
            tagsJson = toJson(event.tags),
            contextJson = toJson(event.context),
            replayId = event.replayId,
            eventTimestamp = occurredAt,
            createdAt = now
        )

        val savedEvent = eventRepository.save(obsEvent)
        livePublisher.publishEvent(savedIssue, savedEvent)
        issueAlertHandler.onIssuePersisted(savedIssue, isNewIssue, wasReopened)
        return savedIssue.id
    }

    private fun buildTitle(event: ReportedEvent): String {
        val type = event.errorType?.trim().orEmpty()
        val msg = event.message?.trim().orEmpty()
        val base = when {
            type.isNotBlank() && msg.isNotBlank() -> "$type: $msg"
            type.isNotBlank() -> type
            msg.isNotBlank() -> msg
            else -> "${event.platform} ${event.eventType}"
        }
        return base.take(500)
    }

    private fun tagString(tags: Map<String, Any?>?, key: String): String? {
        val value = tags?.get(key) ?: return null
        return value.toString().trim().takeIf { it.isNotBlank() }
    }

    private fun toJson(value: Any?): String? {
        if (value == null) return null
        return try {
            objectMapper.writeValueAsString(value)
        } catch (e: Exception) {
            null
        }
    }

    private fun toLocalDateTime(epochMillis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())

    private data class RateBucket(val minute: Long, val count: AtomicLong)
}
