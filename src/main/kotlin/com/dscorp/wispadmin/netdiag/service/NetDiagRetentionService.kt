package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
open class NetDiagRetentionService(
    private val properties: NetDiagProperties,
    private val writer: NetDiagRetentionWriter
) {

    private val logger = LoggerFactory.getLogger(NetDiagRetentionService::class.java)

    data class PurgeResult(
        val oltLogEvents: Int = 0,
        val incidentEvents: Int = 0,
        val alertDecisions: Int = 0,
        val notificationLogs: Int = 0,
        val suppressionWindows: Int = 0
    ) {
        fun total(): Int =
            oltLogEvents + incidentEvents + alertDecisions + notificationLogs + suppressionWindows
    }

    open fun purgeExpired(now: Instant = Instant.now()): PurgeResult {
        val retention = properties.retention
        val result = PurgeResult(
            oltLogEvents = purgeInBatches("olt_log_event", retention.oltLogEventDays, now, writer::purgeOltLogEvents),
            incidentEvents = purgeInBatches("incident_event", retention.incidentEventDays, now, writer::purgeIncidentEvents),
            alertDecisions = purgeInBatches("alert_decision", retention.alertDecisionDays, now, writer::purgeAlertDecisions),
            notificationLogs = purgeInBatches("notification_log", retention.notificationLogDays, now, writer::purgeNotificationLogs),
            suppressionWindows = purgeInBatches("alert_suppression", retention.suppressionDays, now, writer::purgeSuppressionWindows)
        )
        if (result.total() > 0) {
            logger.info(
                "NetDiag retention oltLogEvent={} incidentEvent={} alertDecision={} notificationLog={} suppression={}",
                result.oltLogEvents,
                result.incidentEvents,
                result.alertDecisions,
                result.notificationLogs,
                result.suppressionWindows
            )
        }
        return result
    }

    private fun purgeInBatches(
        table: String,
        days: Int,
        now: Instant,
        delete: (Instant, Int) -> Int
    ): Int {
        if (days <= 0) return 0
        val cutoff = now.minus(days.toLong(), ChronoUnit.DAYS)
        val batchSize = properties.retention.batchSize.coerceAtLeast(1)
        val maxBatches = properties.retention.maxBatchesPerRun.coerceAtLeast(1)
        var deleted = 0
        try {
            repeat(maxBatches) {
                val batch = delete(cutoff, batchSize)
                deleted += batch
                if (batch < batchSize) return deleted
            }
        } catch (ex: Exception) {
            logger.warn("NetDiag retention on {} stopped after {} rows: {}", table, deleted, ex.message)
        }
        return deleted
    }
}
