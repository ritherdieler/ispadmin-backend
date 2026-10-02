package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.wispadmin.acsclient.AcsCpeCoreClient
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2InternetStatusResponse
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2TaskResponse
import com.dscorp.wispadmin.wispadmin.acsclient.CoreOnboardingV2WanCleanupRequest
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WanCleanupProvisioningStageHandlerTest {
    @Test
    fun `static fiber keeps its static WAN and cleans only other WANs`() {
        val json = jacksonObjectMapper()
        val acs = mockk<AcsCpeCoreClient>()
        val request = slot<CoreOnboardingV2WanCleanupRequest>()
        val statusRequest = slot<CoreOnboardingV2WanCleanupRequest>()
        every { acs.enqueueOnboardingV2WanCleanup(capture(request)) } returns
            CoreOnboardingV2TaskResponse("op-12345678", "WAN_CLEANUP", "task-1", "QUEUED")
        every { acs.onboardingV2WanCleanupStatus(capture(statusRequest)) } returns
            CoreOnboardingV2InternetStatusResponse("COMPLETE", "task-1")
        val registration = ProvisioningV2RegistrationSnapshot(
            ProvisioningV2OnuSnapshot("1", "gpon", "1", "1", "F6600R"),
            100, null, null, null, null,
        )
        val registrationJson = json.valueToTree<ObjectNode>(registration).apply {
            put("accessMode", "STATIC_IP")
        }.toString()
        val contact = AcsContactProvisioningResource("dev-1", "F6600R", "fw")
        val snapshots = mapOf(
            "acs-contact" to json.writeValueAsString(contact),
            ProvisioningV2RegistrationService.REGISTRATION_RESOURCE_KEY to registrationJson,
        )
        val context = ProvisioningStageContext(
            ProvisioningOperation("op-12345678", "staging", 42, "ZTEGDC47BFFD"),
            {}, { _, _ -> }, { snapshots[it] },
        )

        assertEquals(StageObservation.SATISFIED, WanCleanupProvisioningStageHandler(acs, json).apply(context))
        assertEquals("static", request.captured.mode)
        assertEquals("static", statusRequest.captured.mode)
    }
}
