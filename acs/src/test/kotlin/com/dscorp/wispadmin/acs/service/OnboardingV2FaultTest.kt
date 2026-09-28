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

    @Test
    fun `a dropped TR-069 session is retried once and any other fault stays terminal`() {
        val dropped = "The TR-069 session was unsuccessfully terminated"

        assertEquals(SessionDropDecision.RETRY, sessionDropDecision(dropped, alreadyRetried = false))
        assertEquals(SessionDropDecision.TERMINAL, sessionDropDecision(dropped, alreadyRetried = true))
        assertEquals(SessionDropDecision.TERMINAL, sessionDropDecision("V2_WAN_CONFLICT", alreadyRetried = false))
    }

    @Test
    fun `internet status is read live and a cached connection status is not trusted`() {
        assertEquals(true, wanStatusNeedsRefresh(compensation = false))
        assertEquals(false, wanStatusNeedsRefresh(compensation = true))
    }
}
