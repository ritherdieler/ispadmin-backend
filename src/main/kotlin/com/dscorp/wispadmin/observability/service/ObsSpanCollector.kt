package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@Service
class ObsSpanCollector(
    private val spanRepository: ObsSpanRepository,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val buffer = ConcurrentLinkedQueue<ObsSpan>()
    private val bufferedCount = AtomicInteger(0)
    private val perTraceCount = ConcurrentHashMap<String, AtomicInteger>()

    fun enqueue(span: ObsSpan) {
        if (!properties.enabled || !properties.tracing.enabled) return
        val traceId = span.traceId ?: return
        if (bufferedCount.get() >= properties.tracing.maxBufferedSpans) return
        val counter = perTraceCount.computeIfAbsent(traceId) { AtomicInteger(0) }
        if (counter.get() >= properties.tracing.maxSpansPerTrace) return
        counter.incrementAndGet()
        if (span.createdAt == null) span.createdAt = LocalDateTime.now()
        buffer.add(span)
        bufferedCount.incrementAndGet()
    }

    fun enqueueAll(spans: List<ObsSpan>) {
        spans.forEach { enqueue(it) }
    }

    fun flush() {
        if (buffer.isEmpty()) return
        val batch = ArrayList<ObsSpan>()
        while (true) {
            val span = buffer.poll() ?: break
            batch.add(span)
            bufferedCount.decrementAndGet()
            if (batch.size >= 1000) break
        }
        if (batch.isEmpty()) return
        try {
            spanRepository.saveAll(batch)
        } catch (e: Exception) {
            log.warn("No se pudieron persistir spans de trazas: {}", e.message)
        } finally {
            perTraceCount.clear()
        }
    }
}
