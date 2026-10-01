package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class ProvisioningRetryPolicyTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advance(duration: Duration) { now = now.plus(duration) }
    }

    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbc = JdbcTemplate(dataSource)
    private val journal = ProvisioningJournal(jdbc, jacksonObjectMapper().findAndRegisterModules())
    private val clock = MutableClock(Instant.parse("2026-10-01T12:00:00Z"))
    private var mikrotik: () -> StageObservation = { StageObservation.SATISFIED }
    private var mikrotikCalls = 0

    init {
        ProvisioningTestSchema.initialize(dataSource)
        journal.insert(ProvisioningOperation("op", "lab", 42, "ZTEGDC47BFFD"))
    }

    private val handlers = ProvisioningStage.values().map { current ->
        object : ProvisioningStageHandler {
            override val stage = current
            override fun reconcile(context: ProvisioningStageContext): StageObservation {
                if (stage != ProvisioningStage.MIKROTIK) return StageObservation.SATISFIED
                mikrotikCalls++
                return mikrotik()
            }
            override fun apply(context: ProvisioningStageContext) = StageObservation.SATISFIED
            override fun compensate(context: ProvisioningStageContext) = StageObservation.SATISFIED
        }
    }
    private val executor = ProvisioningExecutor(journal, handlers, clock = clock)

    private fun advance() = executor.advance("lab", "op")
    private fun current() = requireNotNull(journal.get("lab", "op"))
    private fun mikrotikCheckpoint() = current().checkpoints.first { it.stage == ProvisioningStage.MIKROTIK }
    private fun nextAttemptAt() = jdbc.queryForObject(
        "SELECT next_attempt_at FROM provisioning_v2_operation WHERE operation_id='op'", Long::class.java)

    private fun timeout(): StageObservation = throw java.net.SocketTimeoutException("Read timed out")

    @Test
    fun `retryable failure waits with backoff instead of failing the operation`() {
        mikrotik = ::timeout
        advance()
        advance()

        assertEquals(ProvisioningState.WAITING, current().state)
        assertEquals(CheckpointState.WAITING, mikrotikCheckpoint().state)
        assertEquals("STAGE_EXECUTION_FAILED", mikrotikCheckpoint().failure?.code)
        assertEquals(clock.instant().plusSeconds(5).toEpochMilli(), nextAttemptAt())
        assertTrue(journal.due("lab", clock.instant()).isEmpty())
        assertEquals(listOf("op"), journal.due("lab", clock.instant().plusSeconds(5)))
    }

    @Test
    fun `transient failure recovers automatically on the next attempt`() {
        mikrotik = ::timeout
        advance()
        advance()
        mikrotik = { StageObservation.SATISFIED }
        clock.advance(Duration.ofSeconds(5))
        advance()

        assertEquals(CheckpointState.SUCCEEDED, mikrotikCheckpoint().state)
        assertEquals(null, mikrotikCheckpoint().failure)
    }

    @Test
    fun `retryable failure becomes FAILED after the attempt budget`() {
        mikrotik = ::timeout
        advance()
        repeat(ProvisioningExecutor.MAX_STAGE_ATTEMPTS) {
            advance()
            clock.advance(Duration.ofMinutes(5))
        }

        assertEquals(ProvisioningState.FAILED, current().state)
        assertEquals(ProvisioningExecutor.MAX_STAGE_ATTEMPTS, mikrotikCalls)
        assertEquals(true, mikrotikCheckpoint().failure?.retryable)
    }

    @Test
    fun `non retryable failure fails immediately`() {
        mikrotik = { throw ProvisioningStepException(ProvisioningFailure("ONU_ALREADY_RESERVED", "Reservada", false)) }
        advance()
        advance()

        assertEquals(ProvisioningState.FAILED, current().state)
        assertEquals(1, mikrotikCalls)
    }

    @Test
    fun `waiting longer than the stage deadline fails with STAGE_TIMEOUT`() {
        mikrotik = { StageObservation.WAITING }
        advance()
        advance()
        assertEquals(ProvisioningState.WAITING, current().state)

        clock.advance(ProvisioningExecutor.STAGE_WAIT_DEADLINE.plusSeconds(1))
        advance()

        assertEquals(ProvisioningState.FAILED, current().state)
        assertEquals("STAGE_TIMEOUT", mikrotikCheckpoint().failure?.code)
        assertEquals(true, mikrotikCheckpoint().failure?.retryable)
    }

    @Test
    fun `failed linked registration releases the operator lock`() {
        val target = ProvisioningOnuTarget("olt-1", "GPON", "0", "1", "VSOLVA74", 100)
        val pre = journal.insertPreauthorization(ProvisioningOperation(
            id = "pre", environment = "lab", subscriptionId = null, serial = "VSOL0031C0B6", flowVersion = 3,
            phase = ProvisioningPhase.OLT_AUTHORIZATION, operatorId = 71, registrationRequestKey = "request-key-1",
            onuTarget = target,
        ))
        val ready = journal.updatePreauthorization("lab", pre.id, pre.revision, transform = { it.copy(
            phase = ProvisioningPhase.READY_FOR_FORM, state = ProvisioningState.READY_FOR_FORM,
            oltEvidence = OltProvisioningResource("ext", 0, 1, 7),
            acsContactEvidence = AcsContactProvisioningResource("cpe", "VSOLVA74", "1.0"),
        ) })
        journal.promotePreauthorization("lab", ready.id, 71, 77)
        mikrotik = { throw ProvisioningStepException(ProvisioningFailure("ONU_ALREADY_RESERVED", "Reservada", false)) }

        executor.advance("lab", "pre")
        executor.advance("lab", "pre")

        assertEquals(ProvisioningState.FAILED, journal.get("lab", "pre")?.state)
        assertEquals(null, journal.activeForOperator("lab", 71))
    }

    @Test
    fun `manual retry after timeout restarts the deadline`() {
        mikrotik = { StageObservation.WAITING }
        advance()
        advance()
        clock.advance(ProvisioningExecutor.STAGE_WAIT_DEADLINE.plusSeconds(1))
        advance()
        journal.requestRetry("lab", "op", current().revision)
        advance()

        assertEquals(ProvisioningState.WAITING, current().state)
    }
}
