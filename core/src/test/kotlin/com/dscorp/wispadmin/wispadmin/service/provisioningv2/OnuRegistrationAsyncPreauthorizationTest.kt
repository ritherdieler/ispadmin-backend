package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactResponse
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactState
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuV2AuthorizeResponse
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OnuRegistrationAsyncPreauthorizationTest {
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbc = JdbcTemplate(dataSource)
    private val json = jacksonObjectMapper().findAndRegisterModules()
    private val journal = ProvisioningJournal(jdbc, json)
    private val gateway = mockk<GatewayOnuActivationClient>()
    private val acs = mockk<AcsCpeCoreClient>()
    private val service = OnuRegistrationOperationService(
        journal, ProvisioningResourceStore(jdbc, CrmSecretCipher("unit-test-only")), gateway, acs, mockk(relaxed = true),
        json, GigafiberEnvironmentProperties().apply { tag = "lab" }, mockk(relaxed = true),
        asyncPreauthorization = true,
    )

    init { ProvisioningTestSchema.initialize(dataSource) }

    private fun request(key: String = "request-key-async") = OnuRegistrationStartRequest(
        requestKey = key,
        serial = "VSOL0031C0B6",
        target = ProvisioningOnuTarget("olt-1", "GPON", "0", "1", "VSOLVA74", 100),
    )

    private fun stubRemotes() {
        every { gateway.authorizeV2(any()) } returns GatewayOnuV2AuthorizeResponse("olt-1", 0, 1, 7, true)
        every { acs.onboardingV2Contact(any()) } returns
            CoreOnboardingV2ContactResponse(CoreOnboardingV2ContactState.READY, "cpe-1", "VSOLVA74", "1.0")
    }

    @Test
    fun `async start persists the intent and returns without touching the OLT`() {
        val started = service.start(71, "tecnico", request())

        assertEquals(ProvisioningPhase.OLT_AUTHORIZATION, started.phase)
        assertEquals(ProvisioningState.PENDING, started.state)
        verify(exactly = 0) { gateway.authorizeV2(any()) }
    }

    @Test
    fun `worker authorizes pending preauthorizations up to the ready form`() {
        stubRemotes()
        val started = service.start(71, "tecnico", request())

        service.processPendingPreauthorizations(Instant.now())

        val current = journal.get("lab", started.id)!!
        assertEquals(ProvisioningPhase.READY_FOR_FORM, current.phase)
        verify(exactly = 1) { gateway.authorizeV2(any()) }
    }

    @Test
    fun `stale running preauthorization is resumed but a fresh one is left alone`() {
        stubRemotes()
        val started = service.start(71, "tecnico", request())
        val running = journal.updatePreauthorization("lab", started.id, started.revision, transform = {
            it.copy(state = ProvisioningState.RUNNING)
        }, now = Instant.now())

        service.processPendingPreauthorizations(Instant.now())
        verify(exactly = 0) { gateway.authorizeV2(any()) }

        service.processPendingPreauthorizations(Instant.now().plus(Duration.ofMinutes(10)))
        assertEquals(ProvisioningPhase.READY_FOR_FORM, journal.get("lab", running.id)!!.phase)
    }
}
