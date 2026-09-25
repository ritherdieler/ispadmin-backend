package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.web.client.HttpServerErrorException
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProvisioningExecutorTest {
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val journal = ProvisioningJournal(JdbcTemplate(dataSource), jacksonObjectMapper().findAndRegisterModules())
    private val clock = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC)
    private val applied = mutableSetOf<ProvisioningStage>()
    private val writes = mutableListOf<ProvisioningStage>()
    private val undos = mutableListOf<ProvisioningStage>()
    private var fail: ProvisioningStage? = null
    private var blow: Exception? = null
    private var uncertain = false
    private var cancelDuringApply = false
    init {
        ResourceDatabasePopulator(ClassPathResource("db/migration/V56__provisioning_v2_journal.sql")).execute(dataSource)
        journal.insert(ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD"))
    }
    private val handlers = ProvisioningStage.values().map { current -> object : ProvisioningStageHandler {
        override val stage = current
        override fun reconcile(context: ProvisioningStageContext) = if (stage in applied) StageObservation.SATISFIED else StageObservation.NEEDS_APPLY
        override fun apply(context: ProvisioningStageContext): StageObservation {
            context.assertLease()
            writes += stage
            blow?.let { throw it }
            if (fail == stage) {
                if (uncertain) applied += stage
                throw ProvisioningStepException(ProvisioningFailure("REMOTE_TIMEOUT", "Respuesta no confirmada", true))
            }
            applied += stage
            if (cancelDuringApply) journal.requestCancel("staging", "op", 1)
            return StageObservation.SATISFIED
        }
        override fun compensate(context: ProvisioningStageContext): StageObservation {
            context.assertLease()
            undos += stage
            applied -= stage
            return StageObservation.SATISFIED
        }
    } }
    private fun advance() = ProvisioningExecutor(journal, handlers, clock = clock).advance("staging", "op")
    private fun current() = requireNotNull(journal.get("staging", "op"))

    @Test fun `remote failure keeps the service reason instead of the status envelope`() {
        val body = """{"timestamp":"2026-09-25T08:54:36.892-05:00","status":500,"error":"Internal Server Error","message":"OMCI_READBACK_MISMATCH configType=Invalid vlan=none priority=none profile=none address=absent","path":"/ispadmin/api/olt-gateway/onus/ZTEGDC47BFFD/omci/management"}"""
        blow = HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "",
            HttpHeaders(),
            body.toByteArray(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
        )
        advance()
        val failure = current().checkpoints.first { it.stage == ProvisioningStage.VALIDATE }.failure
        assertEquals("OMCI_READBACK_MISMATCH", failure?.code)
        assertEquals(
            "OMCI_READBACK_MISMATCH configType=Invalid vlan=none priority=none profile=none address=absent",
            failure?.message,
        )
    }

    @Test fun `retry after uncertain write reconciles without duplicating effects`() {
        fail = ProvisioningStage.VALIDATE
        uncertain = true
        advance()
        assertEquals(ProvisioningState.FAILED, current().state)
        journal.requestRetry("staging", "op", 1)
        fail = null
        advance()
        assertEquals(CheckpointState.SUCCEEDED, current().checkpoints.first().state)
        assertEquals(listOf(ProvisioningStage.VALIDATE), writes)
    }

    @Test fun `cancel during remote call preserves its result for compensation`() {
        cancelDuringApply = true
        advance()
        assertEquals(ProvisioningState.CANCEL_REQUESTED, current().state)
        advance()
        assertEquals(ProvisioningState.CANCELLED, current().state)
        assertTrue(applied.isEmpty())
        assertEquals(listOf(ProvisioningStage.VALIDATE), undos)
    }

    @Test fun `cancel before first effect completes without compensations`() {
        journal.requestCancel("staging", "op", 1)
        advance()
        assertEquals(ProvisioningState.CANCELLED, current().state)
        assertTrue(writes.isEmpty() && undos.isEmpty())
    }

    @Test fun `one bounded step per invocation and restart resumes persisted progress`() {
        repeat(ProvisioningStage.values().size) { advance() }
        assertEquals(ProvisioningState.SUCCEEDED, current().state)
        assertEquals(ProvisioningStage.values().toList(), writes)
        advance()
        assertEquals(8, writes.size)
    }
}
