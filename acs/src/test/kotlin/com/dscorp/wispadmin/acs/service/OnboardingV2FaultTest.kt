package com.dscorp.wispadmin.acs.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OnboardingV2FaultTest {
    @Test
    fun `fault reason is the script message and never the provision payload`() {
        val body = """{"message":"V2_WAN_CONFLICT","provisions":[["gf-onboarding-v2-pppoe","{\"password\":\"secret\"}"]]}"""

        val reason = onboardingFaultReason(body)

        assertEquals("V2_WAN_CONFLICT", reason)
        assertFalse(reason!!.contains("secret"))
        assertNull(onboardingFaultReason("""{"message":""}"""))
    }
}
