package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
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
    private var leaseUntil: Long? = null
    init {
        ProvisioningTestSchema.initialize(dataSource)
        journal.insert(ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD"))
    }
    private val handlers = ProvisioningStage.values().map { current -> object : ProvisioningStageHandler {
        override val stage = current
        override fun reconcile(context: ProvisioningStageContext) = if (stage in applied) StageObservation.SATISFIED else StageObservation.NEEDS_APPLY
        override fun apply(context: ProvisioningStageContext): StageObservation {
            context.assertLease()
            if (leaseUntil == null && stage == ProvisioningStage.VALIDATE) {
                leaseUntil = JdbcTemplate(dataSource).queryForObject(
                    "select lease_until from provisioning_v2_operation where operation_id='op'",
                    Long::class.java,
                )
            }
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

    @Test fun `lease outlives the slowest remote stage so a running stage is never reclaimed`() {
        advance()
        assertEquals(clock.instant().toEpochMilli() + ProvisioningExecutor.LEASE_DURATION.toMillis(), leaseUntil)
        assertTrue(ProvisioningExecutor.LEASE_DURATION.toMillis() > 180_000L + 60_000L)
    }

    @Test fun `remote failure keeps the service reason instead of the status envelope`() {
        val body = """{"timestamp":"2026-09-25T08:54:36.892-05:00","status":500,"error":"Internal Server Error","message":"ACS_CONTACT_REJECTED","path":"/api/acs/contact"}"""
        blow = HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "",
            HttpHeaders(),
            body.toByteArray(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
        )
        advance()
        val failure = current().checkpoints.first { it.stage == ProvisioningStage.VALIDATE }.failure
        assertEquals("ACS_CONTACT_REJECTED", failure?.code)
        assertEquals("ACS_CONTACT_REJECTED", failure?.message)
    }

    @Test fun `retry after uncertain write reconciles without duplicating effects`() {
        fail = ProvisioningStage.VALIDATE
        uncertain = true
        advance()
        assertEquals(ProvisioningState.WAITING, current().state)
        fail = null
        ProvisioningExecutor(journal, handlers, clock = Clock.offset(clock, java.time.Duration.ofSeconds(10))).advance("staging", "op")
        assertEquals(CheckpointState.SUCCEEDED, current().checkpoints.first().state)
        assertEquals(listOf(ProvisioningStage.VALIDATE), writes)
    }

    @Test fun `cancel during remote call preserves its result for compensation`() {
        cancelDuringApply = true
        advance()
        assertEquals(ProvisioningState.CANCEL_REQUESTED, current().state)
        advance()
        assertEquals(ProvisioningState.CANCELLING, current().state)
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

    @Test fun `photo cleanup failure keeps operator lock until cancellation retry succeeds`() {
        val target = ProvisioningOnuTarget("olt", "GPON", "0", "1", "VSOLVA74", 100)
        val preauthorization = ProvisioningOperation(
            "cancel-photo", "staging", null, "VSOL0031C0B6", flowVersion = 3,
            phase = ProvisioningPhase.OLT_AUTHORIZATION, operatorId = 71,
            registrationRequestKey = "cancel-request-1", onuTarget = target,
        )
        journal.insertPreauthorization(preauthorization)
        val ready = journal.updatePreauthorization("staging", preauthorization.id, preauthorization.revision, transform = { current ->
            current.copy(phase = ProvisioningPhase.READY_FOR_FORM, state = ProvisioningState.READY_FOR_FORM)
        })
        val resources = ProvisioningResourceStore(JdbcTemplate(dataSource), CrmSecretCipher("unit-test-only"))
        val photoUrl = "https://storage.example/photo?token=private-token"
        resources.savePreauthorizationResource("staging", ready.id, 71, "registration-photo", photoUrl)
        val storage = mockk<FirebaseStorageService>()
        every { storage.deleteByPublicUrl(photoUrl) } throws IllegalStateException("storage unavailable") andThen Unit
        journal.requestCancel("staging", ready.id, ready.revision)

        val executor = ProvisioningExecutor(journal, handlers, resources, clock, storage)
        executor.advance("staging", ready.id)
        assertEquals(ProvisioningState.CANCEL_FAILED, currentOperation(ready.id).state)
        assertEquals("CANCELLATION_CLEANUP_FAILED", currentOperation(ready.id).operationFailure?.code)
        assertEquals(ready.id, journal.activeForOperator("staging", 71)?.id)

        journal.requestCancel("staging", ready.id, ready.revision)
        executor.advance("staging", ready.id)

        assertEquals(ProvisioningState.CANCELLED, currentOperation(ready.id).state)
        assertNull(journal.activeForOperator("staging", 71))
        assertNull(resources.snapshot("staging", ready.id, "registration-photo"))
        verify(exactly = 2) { storage.deleteByPublicUrl(photoUrl) }
    }

    @Test fun `linked subscription photo is retained for the hard cleanup step`() {
        val operation = ProvisioningOperation(
            id = "cancel-linked-photo",
            environment = "staging",
            subscriptionId = 42,
            serial = "VSOL0031C0B6",
            flowVersion = 3,
            operatorId = 71,
        )
        journal.insert(operation)
        val resources = ProvisioningResourceStore(JdbcTemplate(dataSource), CrmSecretCipher("unit-test-only"))
        val photoUrl = "https://storage.example/linked-photo"
        resources.captureInitial(operation, "registration-photo", photoUrl)
        journal.requestCancel("staging", operation.id, operation.revision)
        val storage = mockk<FirebaseStorageService>(relaxed = true)

        ProvisioningExecutor(journal, handlers, resources, clock, storage).advance("staging", operation.id)

        assertEquals(ProvisioningState.CANCELLED, journal.get("staging", operation.id)?.state)
        verify(exactly = 0) { storage.deleteByPublicUrl(photoUrl) }
    }

    @Test fun `last compensation keeps operation active until photo and resources are cleaned`() {
        val operation = ProvisioningOperation(
            id = "cancel-final-cleanup",
            environment = "staging",
            subscriptionId = 43,
            serial = "VSOL0031C0B6",
        )
        journal.insert(operation)
        val resources = ProvisioningResourceStore(JdbcTemplate(dataSource), CrmSecretCipher("unit-test-only"))
        val photoUrl = "https://storage.example/cancel-final-photo"
        resources.captureInitial(operation, "registration-photo", photoUrl)
        val executor = ProvisioningExecutor(journal, handlers, clock = clock)
        executor.advance("staging", operation.id)
        val touched = requireNotNull(journal.get("staging", operation.id))
        val storage = mockk<FirebaseStorageService>(relaxed = true)
        journal.requestCancel("staging", operation.id, touched.revision)

        ProvisioningExecutor(journal, handlers, resources, clock, storage).advance("staging", operation.id)

        assertEquals(ProvisioningState.CANCELLING, journal.get("staging", operation.id)?.state)
        assertEquals(photoUrl, resources.preauthorizationPhotoUrl("staging", operation.id))
        verify(exactly = 0) { storage.deleteByPublicUrl(photoUrl) }

        ProvisioningExecutor(journal, handlers, resources, clock, storage).advance("staging", operation.id)

        assertEquals(ProvisioningState.CANCELLED, journal.get("staging", operation.id)?.state)
        assertNull(resources.snapshot("staging", operation.id, "registration-photo"))
        verify(exactly = 0) { storage.deleteByPublicUrl(photoUrl) }
    }

    private fun currentOperation(id: String) = requireNotNull(journal.get("staging", id))

    @Test fun `one bounded step per invocation and restart resumes persisted progress`() {
        repeat(ProvisioningStage.values().size) { advance() }
        assertEquals(ProvisioningState.SUCCEEDED, current().state)
        assertEquals(ProvisioningStage.values().toList(), writes)
        advance()
        assertEquals(ProvisioningStage.values().size, writes.size)
    }
}
