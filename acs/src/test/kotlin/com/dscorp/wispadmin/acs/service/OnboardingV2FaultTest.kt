package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.genieacs.wanOwnerMatches
import com.dscorp.wispadmin.acs.genieacs.GenieAcsWanPppConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `fault reason includes the rejected parameter details when GenieACS returns an SPV fault`() {
        val body = """{"code":"9003","message":"Invalid arguments","detail":{"setParameterValuesFault":[{"parameterName":"InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1.Alias","faultCode":"9007","faultString":"Invalid parameter value"}]}}"""

        assertEquals(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1.Alias: Invalid parameter value (9007)",
            onboardingFaultReason(body),
        )
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

    @Test
    fun `static internet completes only when the owned IP WAN reports the assigned address`() {
        val wan = GenieAcsWanPppConnection(
            path = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1",
            name = "GFv2-op123", connectionStatus = "Connected", externalIp = "192.168.30.21",
        )

        assertFalse(internetWanConfirmed(wan, "static", "192.168.30.20"))
        assertTrue(internetWanConfirmed(wan.copy(externalIp = "192.168.30.20"), "static", "192.168.30.20"))
        assertFalse(internetWanConfirmed(wan, "pppoe", null))
    }

    @Test
    fun `v2 ownership accepts the safe CPE name marker in Alias`() {
        assertTrue(wanOwnerMatches("2_INTERNET_R_VID_100", "GFv2-op123", "GFv2-op123"))
        assertTrue(wanOwnerMatches("GFv2-op123", null, "GFv2-op123"))
        assertFalse(wanOwnerMatches("2_INTERNET_R_VID_100", "GFv2-other", "GFv2-op123"))
    }

    @Test
    fun `v2 ownership marker fits the CPE Alias limit`() {
        val marker = onboardingOwnerMarker("ba33deae-99c9-45ed-808c-b1172ae1c11f")

        assertEquals(32, marker.length)
        assertEquals("GFv2-ba33deae99c945ed808cb1172ae", marker)
    }
}
