package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagPollService(
    private val targetRepository: NetDiagTargetRepository,
    private val probeRunRepository: NetDiagProbeRunRepository,
    private val pollAdapter: MikrotikPollAdapter,
    private val signalExtractor: AlertSignalExtractor,
    private val alertEvaluator: AlertEvaluator,
    private val properties: NetDiagProperties
) {

    private val logger = LoggerFactory.getLogger(NetDiagPollService::class.java)

    fun pollAllEnabledTargets() {
        val targets = targetRepository.findByEnabledTrue()
        if (targets.isEmpty()) {
            return
        }
        val concurrency = properties.poll.concurrency.coerceAtLeast(1)
        val executor = Executors.newFixedThreadPool(concurrency)
        try {
            val futures = targets.map { target ->
                executor.submit<Unit> {
                    pollOne(target)
                }
            }
            futures.forEach { future: Future<Unit> ->
                try {
                    future.get(properties.poll.intervalMs.coerceAtLeast(1000), TimeUnit.MILLISECONDS)
                } catch (ex: Exception) {
                    logger.warn("NetDiag poll future failed: {}", ex.message)
                }
            }
        } finally {
            executor.shutdownNow()
        }
        evaluateStale(targets)
    }

    fun pollOne(target: NetDiagTarget) {
        val jitterMax = properties.poll.jitterMs.coerceAtLeast(0)
        if (jitterMax > 0) {
            Thread.sleep(Random.nextLong(0, jitterMax.toLong() + 1))
        }
        val targetId = target.id ?: return
        try {
            val result = pollAdapter.poll(target)
            val signals = if (result.status == "SUCCESS" && result.snapshot != null) {
                signalExtractor.fromSnapshot(targetId, result.snapshot)
            } else {
                signalExtractor.fromPollFailure(
                    targetId,
                    result.errorReasonCode ?: "COMMAND_ERROR",
                    result.probeRun.error
                )
            }
            alertEvaluator.evaluate(targetId, signals)
        } catch (ex: Exception) {
            logger.warn("NetDiag poll failed for target {}: {}", targetId, ex.message)
            alertEvaluator.evaluate(
                targetId,
                signalExtractor.fromPollFailure(targetId, "COMMAND_ERROR", ex.message)
            )
        }
    }

    @Transactional
    fun purgeOldProbeRuns(): Int {
        val days = properties.retention.probeRunDays.coerceAtLeast(1)
        val cutoff = Instant.now().minus(Duration.ofDays(days.toLong()))
        return probeRunRepository.deleteByStartedAtBefore(cutoff)
    }

    private fun evaluateStale(targets: List<NetDiagTarget>) {
        val multiplier = properties.alert.staleMultiplier.coerceAtLeast(1)
        val now = Instant.now()
        targets.forEach { target ->
            val targetId = target.id ?: return@forEach
            val interval = target.pollIntervalMs.coerceAtLeast(properties.poll.intervalMs)
            val threshold = Duration.ofMillis(interval * multiplier)
            val latest = probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(targetId).orElse(null)
            if (latest == null || Duration.between(latest.startedAt, now) > threshold) {
                val details = "lastProbeAt=${latest?.startedAt};thresholdMs=${threshold.toMillis()}"
                alertEvaluator.evaluate(targetId, listOf(signalExtractor.pollStale(targetId, details)))
            }
        }
    }
}
