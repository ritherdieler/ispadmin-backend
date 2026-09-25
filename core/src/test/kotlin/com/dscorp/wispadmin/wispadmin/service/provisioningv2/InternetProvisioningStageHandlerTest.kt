package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetStatusResponse
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InternetProvisioningStageHandlerTest {
    @Test
    fun `acs internet failure reaches the client with the equipment reason`() {
        val json = jacksonObjectMapper()
        val acs = mockk<AcsCpeCoreClient>()
        val subscriptions = mockk<SubscriptionRepository>()
        val cipher = CrmSecretCipher("unit-test-key")
        val subscription = Subscription(id = 42, equipmentCondition = EquipmentCondition.LOAN).apply {
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
            1, 100, null, null, null, null,
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
