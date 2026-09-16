package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.util.MultiValueMap

class SmartOltMgmtIpDhcpApplierTest {

    @Test
    fun `tras authorize vlan 100 postea set_onu_mgmt_ip_dhcp con vlan 1000`() {
        val calls = mutableListOf<Pair<String, MultiValueMap<String, Any>>>()
        val applier = SmartOltMgmtIpDhcpApplier(
            policy = SmartOltMgmtVlanPolicy(setOf("100"), "1000"),
            poster = { path, body -> calls += path to body },
        )

        applier.afterAuthorize(
            OnuAuthorizationRequest(
                olt_id = "2",
                pon_type = "gpon",
                board = "1",
                port = "6",
                sn = "ZTEGDC47BFFD",
                vlan = "100",
                onu_type = "F6600RV9.0.21",
                zone = "Zone 1",
                name = "Cliente",
                onu_mode = "Routing",
                custom_profile = "Generic_1",
            )
        )

        assertEquals(1, calls.size)
        assertEquals("onu/set_onu_mgmt_ip_dhcp/ZTEGDC47BFFD", calls[0].first)
        assertEquals(listOf("1000"), calls[0].second["vlan"])
    }

    @Test
    fun `vlan distinta de 100 no postea mgmt dhcp`() {
        val calls = mutableListOf<Pair<String, MultiValueMap<String, Any>>>()
        val applier = SmartOltMgmtIpDhcpApplier(
            policy = SmartOltMgmtVlanPolicy(setOf("100"), "1000"),
            poster = { path, body -> calls += path to body },
        )

        applier.afterAuthorize(
            OnuAuthorizationRequest(
                olt_id = "2",
                pon_type = "gpon",
                board = "1",
                port = "0",
                sn = "HWTC0086CD49",
                vlan = "1",
                onu_type = "EG8145V5",
                zone = "Zone 1",
                name = "Cliente",
                onu_mode = "Routing",
                custom_profile = "Generic_1",
            )
        )

        assertTrue(calls.isEmpty())
    }
}
