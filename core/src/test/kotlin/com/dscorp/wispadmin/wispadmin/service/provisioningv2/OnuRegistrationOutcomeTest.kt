package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

class OnuRegistrationOutcomeTest {
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbc = JdbcTemplate(dataSource)
    private val json = jacksonObjectMapper().findAndRegisterModules()
    private val journal = ProvisioningJournal(jdbc, json)
    private val service = OnuRegistrationOperationService(
        journal, ProvisioningResourceStore(jdbc, CrmSecretCipher("unit-test-only")), mockk(), mockk(), mockk(relaxed = true),
        json, GigafiberEnvironmentProperties().apply { tag = "lab" }, mockk(relaxed = true),
    )

    init { ProvisioningTestSchema.initialize(dataSource) }

    private fun linkedOperation(): ProvisioningOperation {
        val pre = journal.insertPreauthorization(ProvisioningOperation(
            id = "op-outcome", environment = "lab", subscriptionId = null, serial = "VSOL0031C0B6", flowVersion = 3,
            phase = ProvisioningPhase.OLT_AUTHORIZATION, operatorId = 71, registrationRequestKey = "request-key-9",
            onuTarget = ProvisioningOnuTarget("olt-1", "GPON", "0", "1", "VSOLVA74", 100),
        ))
        val ready = journal.updatePreauthorization("lab", pre.id, pre.revision, transform = { it.copy(
            phase = ProvisioningPhase.READY_FOR_FORM, state = ProvisioningState.READY_FOR_FORM,
            oltEvidence = OltProvisioningResource("ext", 0, 1, 7),
            acsContactEvidence = AcsContactProvisioningResource("cpe", "VSOLVA74", "1.0"),
        ) })
        return journal.promotePreauthorization("lab", ready.id, 71, 501)
    }

    @Test
    fun `owner can read the outcome of a linked operation`() {
        linkedOperation()

        val outcome = service.outcome(71, "op-outcome")

        assertEquals("op-outcome", outcome.operationId)
        assertEquals(501, outcome.subscriptionId)
        assertEquals(ProvisioningPhase.PROVISIONING, outcome.phase)
        assertEquals("RUNNING", outcome.outcome)
    }

    @Test
    fun `admin listing includes linked operations filtered by state and age`() {
        linkedOperation()

        val running = service.adminOperations(setOf(ProvisioningState.PENDING), olderThanMinutes = 0)
        val none = service.adminOperations(setOf(ProvisioningState.FAILED), olderThanMinutes = 0)

        val item = running.single()
        assertEquals("op-outcome", item.operationId)
        assertEquals(501, item.subscriptionId)
        assertEquals("VALIDATE", item.currentStage)
        assertEquals(true, none.isEmpty())
    }

    @Test
    fun `other operators cannot read the outcome`() {
        linkedOperation()

        assertThrows(NoSuchElementException::class.java) { service.outcome(99, "op-outcome") }
    }
}
