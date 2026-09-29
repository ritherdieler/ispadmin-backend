package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactResponse
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2ContactState
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuActivationClient
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuV2AuthorizeRequest
import com.dscorp.wispadmin.wispadmin.oltclient.GatewayOnuV2AuthorizeResponse
import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

class OnuRegistrationOperationServiceTest {
    private val dataSource = DriverManagerDataSource(
        "jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "",
    )
    private val jdbc = JdbcTemplate(dataSource)
    private val json = jacksonObjectMapper().findAndRegisterModules()
    private val journal = ProvisioningJournal(jdbc, json)
    private val resources = ProvisioningResourceStore(jdbc, CrmSecretCipher("unit-test-only"))
    private val gateway = mockk<GatewayOnuActivationClient>()
    private val acs = mockk<AcsCpeCoreClient>()
    private val storage = mockk<FirebaseStorageService>(relaxed = true)
    private val environment = GigafiberEnvironmentProperties().apply { tag = "lab" }
    private val service = OnuRegistrationOperationService(journal, resources, gateway, acs, storage, json, environment)

    init {
        ProvisioningTestSchema.initialize(dataSource)
    }

    @Test
    fun `ACS wait does not auto schedule and retry checks ACS once`() {
        every { gateway.authorizeV2(any<GatewayOnuV2AuthorizeRequest>()) } returns
            GatewayOnuV2AuthorizeResponse("olt-1", 0, 1, 7, true)
        every { acs.onboardingV2Contact(any<CoreOnboardingV2ContactRequest>()) } returnsMany listOf(
            CoreOnboardingV2ContactResponse(CoreOnboardingV2ContactState.WAITING),
            CoreOnboardingV2ContactResponse(CoreOnboardingV2ContactState.READY, "cpe-1", "VSOLVA74", "1.0"),
        )

        val waiting = service.start(12, "tecnico", request())

        assertEquals(ProvisioningPhase.WAITING_FOR_ACS, waiting.phase)
        assertEquals(ProvisioningState.WAITING, waiting.state)
        assertTrue(journal.due("lab", java.time.Instant.now()).isEmpty())

        val ready = service.retryAcs(12, waiting.id, waiting.revision)

        assertEquals(ProvisioningPhase.READY_FOR_FORM, ready.phase)
        assertEquals(ProvisioningState.READY_FOR_FORM, ready.state)
        verify(exactly = 2) { acs.onboardingV2Contact(any<CoreOnboardingV2ContactRequest>()) }
    }

    @Test
    fun `start retry after lost response returns the same active ONU operation`() {
        every { gateway.authorizeV2(any<GatewayOnuV2AuthorizeRequest>()) } returns
            GatewayOnuV2AuthorizeResponse("olt-1", 0, 1, 7, true)
        every { acs.onboardingV2Contact(any<CoreOnboardingV2ContactRequest>()) } returns
            CoreOnboardingV2ContactResponse(CoreOnboardingV2ContactState.WAITING)

        val first = service.start(12, "tecnico", request())
        val retried = service.start(12, "tecnico", request().copy(requestKey = "client-request-2"))

        assertEquals(first.id, retried.id)
        verify(exactly = 1) { gateway.authorizeV2(any<GatewayOnuV2AuthorizeRequest>()) }
        verify(exactly = 1) { acs.onboardingV2Contact(any<CoreOnboardingV2ContactRequest>()) }
    }

    @Test
    fun `complete registration form is encrypted and restored on its preauthorization`() {
        every { gateway.authorizeV2(any<GatewayOnuV2AuthorizeRequest>()) } returns
            GatewayOnuV2AuthorizeResponse("olt-1", 0, 1, 7, true)
        every { acs.onboardingV2Contact(any<CoreOnboardingV2ContactRequest>()) } returns
            CoreOnboardingV2ContactResponse(CoreOnboardingV2ContactState.READY, "cpe-2", "VSOLVA74", "1.0")
        val operation = service.start(12, "tecnico", request())
        val form = registrationForm()

        service.saveDraft(12, operation.id, json.valueToTree(form))
        val restored = requireNotNull(service.draft(12, operation.id))

        assertEquals("dni-private-901", restored.path("dni").asText())
        assertEquals("wifi-private-password", restored.path("wifiPassword24").asText())
        assertEquals(operation.id, restored.path("registrationOperationId").asText())
        val encrypted = jdbc.queryForObject("SELECT snapshot_cipher FROM provisioning_v2_resource WHERE operation_id=? AND resource_key='registration-draft'",
            String::class.java, operation.id)
        assertFalse(requireNotNull(encrypted).contains("dni-private-901"))
        assertFalse(requireNotNull(encrypted).contains("wifi-private-password"))
    }

    @Test
    fun `partial form draft is accepted encrypted and restored before required fields exist`() {
        every { gateway.authorizeV2(any<GatewayOnuV2AuthorizeRequest>()) } returns
            GatewayOnuV2AuthorizeResponse("olt-1", 0, 1, 7, true)
        every { acs.onboardingV2Contact(any<CoreOnboardingV2ContactRequest>()) } returns
            CoreOnboardingV2ContactResponse(CoreOnboardingV2ContactState.READY, "cpe-3", "VSOLVA74", "1.0")
        val operation = service.start(12, "tecnico", request())
        val partial = json.readTree("""{"firstName":"CLIENTE","dni":"dni-draft-123","facadePhotoUrl":"https://untrusted.invalid/photo"}""")

        service.saveDraft(12, operation.id, partial)
        val restored = requireNotNull(service.draft(12, operation.id))

        assertEquals("CLIENTE", restored.path("firstName").asText())
        assertEquals(operation.id, restored.path("registrationOperationId").asText())
        assertTrue(restored.path("facadePhotoUrl").isMissingNode || restored.path("facadePhotoUrl").isNull)
        val encrypted = jdbc.queryForObject(
            "SELECT snapshot_cipher FROM provisioning_v2_resource WHERE operation_id=? AND resource_key='registration-draft'",
            String::class.java,
            operation.id,
        )
        assertFalse(requireNotNull(encrypted).contains("dni-draft-123"))
        assertFalse(requireNotNull(encrypted).contains("untrusted.invalid"))
    }

    @Test
    fun `ACS diagnostics redact access tokens and API keys`() {
        every { gateway.authorizeV2(any<GatewayOnuV2AuthorizeRequest>()) } returns
            GatewayOnuV2AuthorizeResponse("olt-1", 0, 1, 7, true)
        every { acs.onboardingV2Contact(any<CoreOnboardingV2ContactRequest>()) } throws
            IllegalStateException("Request rejected token=raw-access-token api_key=raw-api-key")

        val operation = service.start(12, "tecnico", request())
        val details = operation.checkpoints[ProvisioningStage.ACS_CONTACT.ordinal].failure?.technicalDetails

        assertTrue(requireNotNull(details).contains("token=<redacted>"))
        assertTrue(details.contains("api_key=<redacted>"))
        assertFalse(details.contains("raw-access-token"))
        assertFalse(details.contains("raw-api-key"))
    }

    @Test
    fun `retry resumes a durable operation interrupted during OLT authorization`() {
        every { gateway.authorizeV2(any<GatewayOnuV2AuthorizeRequest>()) } returns
            GatewayOnuV2AuthorizeResponse("olt-1", 0, 1, 7, true)
        every { acs.onboardingV2Contact(any<CoreOnboardingV2ContactRequest>()) } returns
            CoreOnboardingV2ContactResponse(CoreOnboardingV2ContactState.READY, "cpe-recovered", "VSOLVA74", "1.0")
        val interrupted = ProvisioningOperation(
            id = "interrupted-olt-auth-0001",
            environment = "lab",
            subscriptionId = null,
            serial = "VSOL0031C0B6",
            flowVersion = 3,
            managementMode = ManagementProvisioningMode.PRECONFIGURED,
            phase = ProvisioningPhase.OLT_AUTHORIZATION,
            operatorId = 12,
            operatorUsername = "tecnico",
            registrationRequestKey = "request-interrupted-1",
            onuTarget = request().target,
        )
        journal.insertPreauthorization(interrupted)
        val crashed = journal.updatePreauthorization("lab", interrupted.id, interrupted.revision, transform = { current ->
            current.copy(
                state = ProvisioningState.RUNNING,
                checkpoints = current.checkpoints.map { checkpoint ->
                    if (checkpoint.stage == ProvisioningStage.OLT) checkpoint.copy(
                        state = CheckpointState.RUNNING, attempts = 1, touched = true,
                    ) else checkpoint
                },
            )
        })

        val recovered = service.retryAcs(12, interrupted.id, crashed.revision)

        assertEquals(ProvisioningPhase.READY_FOR_FORM, recovered.phase)
        assertEquals(CheckpointState.SUCCEEDED, recovered.checkpoints[ProvisioningStage.OLT.ordinal].state)
        assertEquals(2, recovered.checkpoints[ProvisioningStage.OLT.ordinal].attempts)
        verify(exactly = 1) { gateway.authorizeV2(match { it.operationId == interrupted.id }) }
    }

    private fun request() = OnuRegistrationStartRequest(
        requestKey = "client-request-1",
        serial = "VSOL0031C0B6",
        target = ProvisioningOnuTarget(
            oltId = "olt-1", ponType = "GPON", board = "0", port = "1", onuType = "VSOLVA74", vlan = 100,
        ),
    )

    private fun registrationForm() = com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest(
        firstName = "Cliente",
        lastName = "Prueba",
        dni = "dni-private-901",
        address = "Calle de laboratorio",
        phone = "900000000",
        subscriptionDate = 1_790_640_000_000,
        planId = 1,
        additionalDeviceIds = emptyList(),
        placeId = 1,
        location = GeoLocation(-12.0, -77.0),
        technicianId = 12,
        hostDeviceId = 1,
        onu = OnuDto(olt_id = "olt-1", pon_type = "GPON", board = "0", port = "1", onu_type_name = "VSOLVA74", sn = "VSOL0031C0B6"),
        installationType = InstallationType.FIBER,
        wifiPassword24 = "wifi-private-password",
    )
}
