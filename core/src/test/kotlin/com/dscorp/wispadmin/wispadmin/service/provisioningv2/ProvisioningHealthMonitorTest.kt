package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.observability.ObservabilityReporter
import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProvisioningHealthMonitorTest {
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbc = JdbcTemplate(dataSource)
    private val json = jacksonObjectMapper().findAndRegisterModules()
    private val journal = ProvisioningJournal(jdbc, json)
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val reported = mutableListOf<ReportedEvent>()
    private val reporter = object : ObservabilityReporter {
        override fun report(event: ReportedEvent) { reported += event }
    }
    private val heartbeat = ProvisioningWorkerHeartbeat()

    init { ProvisioningTestSchema.initialize(dataSource) }

    private fun monitor(workerEnabled: Boolean = true) = ProvisioningHealthMonitor(
        journal = journal, environment = "lab", heartbeat = heartbeat, reporter = reporter,
        clock = Clock.fixed(now, ZoneOffset.UTC), workerEnabled = workerEnabled,
    )

    private fun store(operation: ProvisioningOperation) {
        journal.insert(operation.copy(state = ProvisioningState.PENDING, checkpoints = ProvisioningStage.values().map { StageCheckpoint(it) }))
        jdbc.update("UPDATE provisioning_v2_operation SET operation_json=?, state=? WHERE operation_id=?",
            json.writeValueAsString(operation), operation.state.name, operation.id)
    }

    @Test
    fun `healthy pipeline does not emit events`() {
        heartbeat.beat(now.minusSeconds(5))
        store(ProvisioningOperation("fresh", "lab", 1, "ZTEG00000001", state = ProvisioningState.RUNNING, updatedAt = now.minusSeconds(30)))

        monitor().evaluate()

        assertTrue(reported.isEmpty())
    }

    @Test
    fun `stalled, long waiting and recent failed operations are reported with counts only`() {
        heartbeat.beat(now.minusSeconds(5))
        store(ProvisioningOperation("stalled", "lab", 1, "ZTEG00000001", state = ProvisioningState.RUNNING,
            updatedAt = now.minus(Duration.ofMinutes(20))))
        store(ProvisioningOperation("waiting", "lab", 2, "ZTEG00000002", state = ProvisioningState.WAITING, updatedAt = now,
            checkpoints = ProvisioningStage.values().map {
                if (it == ProvisioningStage.WIFI) StageCheckpoint(it, CheckpointState.WAITING, attempts = 1,
                    waitingSinceEpochMs = now.minus(Duration.ofMinutes(20)).toEpochMilli())
                else StageCheckpoint(it, CheckpointState.SUCCEEDED)
            }))
        store(ProvisioningOperation("failed", "lab", 3, "ZTEG00000003", state = ProvisioningState.FAILED,
            updatedAt = now.minus(Duration.ofMinutes(10))))

        monitor().evaluate()

        val event = reported.single()
        assertEquals("provisioning.health", event.message)
        assertEquals("warning", event.severity)
        assertEquals(1, event.tags?.get("stalledOperations"))
        assertEquals(1, event.tags?.get("longWaitingOperations"))
        assertEquals(1, event.tags?.get("failedLastHour"))
        assertEquals("fiber-onboarding", event.tags?.get("feature"))
        assertTrue(event.tags.orEmpty().keys.none { it.contains("serial") || it.contains("subscriptionId") })
    }

    @Test
    fun `missing worker heartbeat is reported when the worker should be running`() {
        heartbeat.beat(now.minus(Duration.ofMinutes(5)))

        monitor(workerEnabled = true).evaluate()

        assertEquals(true, reported.single().tags?.get("workerStale"))
    }
}
