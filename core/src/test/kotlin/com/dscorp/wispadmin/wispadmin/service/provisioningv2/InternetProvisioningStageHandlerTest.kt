package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetStatusResponse
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetStatusRequest
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2TaskResponse
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InternetProvisioningStageHandlerTest {
    @Test
    fun `static fiber sends its assigned IP and pool network to GenieACS without PPPoE credentials`() {
        val json = jacksonObjectMapper()
        val acs = mockk<AcsCpeCoreClient>()
        val subscriptions = mockk<SubscriptionRepository>()
        val cipher = CrmSecretCipher("unit-test-key")
        val subscription = Subscription(id = 42, equipmentCondition = EquipmentCondition.LOAN).apply {
            accessMode = AccessMode.STATIC_IP
            ip = "192.168.30.20"
            ipPool = IpPool(id = 1, ipSegment = "192.168.30.0/24")
        }
        every { subscriptions.lockIdentityOwner(42) } returns subscription
        val captured = slot<CoreOnboardingV2InternetRequest>()
        val statusRequest = slot<CoreOnboardingV2InternetStatusRequest>()
        every { acs.enqueueOnboardingV2Internet(capture(captured)) } returns
            CoreOnboardingV2TaskResponse("op-12345678", "INTERNET", "task-1", "QUEUED")
        every { acs.onboardingV2InternetStatus(capture(statusRequest), false) } returns
            CoreOnboardingV2InternetStatusResponse("COMPLETE", "task-1")
        val contact = AcsContactProvisioningResource("dev-1", "F6600R", "fw")
        val registration = ProvisioningV2RegistrationSnapshot(
            ProvisioningV2OnuSnapshot("1", "gpon", "1", "1", "F6600R"),
            100, null, null, null, null,
        )
        val snapshots = mapOf(
            "acs-contact" to json.writeValueAsString(contact),
            ProvisioningV2RegistrationService.REGISTRATION_RESOURCE_KEY to json.writeValueAsString(registration),
        )
        val context = ProvisioningStageContext(
            ProvisioningOperation("op-12345678", "staging", 42, "ZTEGDC47BFFD"),
            {}, { _, _ -> }, { snapshots[it] },
        )

        assertEquals(StageObservation.SATISFIED,
            InternetProvisioningStageHandler(subscriptions, cipher, acs, json).apply(context))

        val sent = json.valueToTree<com.fasterxml.jackson.databind.JsonNode>(captured.captured)
        assertEquals("static", sent.path("mode").asText())
        assertEquals("192.168.30.20", sent.path("ip").asText())
        assertEquals("255.255.255.0", sent.path("subnetMask").asText())
        assertEquals("192.168.30.1", sent.path("gateway").asText())
        assertEquals("8.8.8.8,8.8.4.4", sent.path("dns").asText())
        assertEquals("", sent.path("username").asText())
        assertEquals("", sent.path("password").asText())
        assertEquals("static", statusRequest.captured.mode)
        assertEquals("192.168.30.20", statusRequest.captured.ip)

        subscription.ip = "192.168.31.20"
        val invalid = assertThrows(ProvisioningStepException::class.java) {
            InternetProvisioningStageHandler(subscriptions, cipher, acs, json).apply(context)
        }
        assertEquals("STATIC_IP_OUTSIDE_POOL", invalid.failure.code)
    }

    @Test
    fun `acs internet failure reaches the client with the equipment reason`() {
        val json = jacksonObjectMapper()
        val acs = mockk<AcsCpeCoreClient>()
        val subscriptions = mockk<SubscriptionRepository>()
        val cipher = CrmSecretCipher("unit-test-key")
        val subscription = Subscription(id = 42, equipmentCondition = EquipmentCondition.LOAN).apply {
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf42"
            pppoePasswordEnc = cipher.encrypt("secret")
        }
        every { subscriptions.lockIdentityOwner(42) } returns subscription
        every { acs.onboardingV2InternetStatus(any(), false) } returns
            CoreOnboardingV2InternetStatusResponse("FAILED", "task-1", "V2_WAN_CONFLICT")
        val contact = AcsContactProvisioningResource("dev-1", "F6600R", "fw")
        val internet = InternetProvisioningResource("task-1", "dev-1", "F6600R", "fw")
        val registration = ProvisioningV2RegistrationSnapshot(
            ProvisioningV2OnuSnapshot("1", "gpon", "1", "1", "F6600R"),
            100, null, null, null, null,
        )
        val snapshots = mapOf(
            "internet" to json.writeValueAsString(internet),
            "acs-contact" to json.writeValueAsString(contact),
            ProvisioningV2RegistrationService.REGISTRATION_RESOURCE_KEY to json.writeValueAsString(registration),
        )
        val context = ProvisioningStageContext(
            ProvisioningOperation("op-12345678", "staging", 42, "ZTEGDC47BFFD"),
            {}, { _, _ -> }, { snapshots[it] },
        )

        val error = assertThrows(ProvisioningStepException::class.java) {
            InternetProvisioningStageHandler(subscriptions, cipher, acs, json).reconcile(context)
        }

        assertEquals("V2_WAN_CONFLICT", error.failure.code)
        assertTrue(error.failure.message.contains("V2_WAN_CONFLICT"))
        assertTrue(error.failure.message != "No se pudo confirmar la tarea PPPoE. Consulte el historial de la operación.")
    }
}
