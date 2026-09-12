package com.dscorp.wispadmin.wispadmin.service.mikrotik

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DebtorCutRulePlacementTest {

    @Test
    fun placeBeforeId_is_the_first_blanket_in_interface_accept() {
        val rules = listOf(
            rule("*1", action = "accept", dstPort = "7547", srcAddress = "10.255.255.2", comment = "GenieACS CR"),
            rule("*3", action = "accept", dstAddress = "10.11.104.0/24", srcAddress = "10.255.255.0/30", comment = "WG"),
            rule("*11", action = "accept", inInterfaceList = "OLT-VLAN100", comment = "OLT sfp-sfpplus2 forward"),
            rule("*12", action = "accept", outInterface = "vlan100-olt", comment = "OLT sfp-sfpplus2 return"),
            rule("*20", action = "accept", inInterface = "LAN-VLAN1", comment = "LAN-VLAN1 forward"),
            rule("*30", action = "drop", srcAddressList = "deudores", comment = DebtorCutRulePlacement.DROP_COMMENT),
        )

        assertEquals("*11", DebtorCutRulePlacement.placeBeforeId(rules))
    }

    @Test
    fun placeBeforeId_skips_the_existing_drop_rule() {
        val rules = listOf(
            rule("*30", action = "drop", srcAddressList = "deudores", comment = DebtorCutRulePlacement.DROP_COMMENT),
            rule("*11", action = "accept", inInterfaceList = "OLT-VLAN100", comment = "OLT forward"),
        )

        assertEquals("*11", DebtorCutRulePlacement.placeBeforeId(rules))
    }

    @Test
    fun placeBeforeId_prefers_in_interface_over_later_lan_accept() {
        val rules = listOf(
            rule("*7", action = "accept", inInterface = "LAN-VLAN1", comment = "LAN-VLAN1 forward"),
            rule("*8", action = "accept", outInterface = "LAN-VLAN1", comment = "LAN-VLAN1 return"),
        )

        assertEquals("*7", DebtorCutRulePlacement.placeBeforeId(rules))
    }

    @Test
    fun placeBeforeId_is_null_when_there_is_no_blanket_accept() {
        val rules = listOf(
            rule("*1", action = "accept", dstPort = "7547", srcAddress = "10.255.255.2"),
            rule("*2", action = "drop", srcAddressList = "deudores", comment = DebtorCutRulePlacement.DROP_COMMENT),
        )

        assertNull(DebtorCutRulePlacement.placeBeforeId(rules))
    }

    private fun rule(
        id: String,
        action: String,
        comment: String = "",
        srcAddress: String? = null,
        dstAddress: String? = null,
        srcAddressList: String? = null,
        dstPort: String? = null,
        inInterface: String? = null,
        inInterfaceList: String? = null,
        outInterface: String? = null,
    ): Map<String, String> {
        val row = mutableMapOf(
            ".id" to id,
            "chain" to "forward",
            "action" to action,
            "comment" to comment,
        )
        srcAddress?.let { row["src-address"] = it }
        dstAddress?.let { row["dst-address"] = it }
        srcAddressList?.let { row["src-address-list"] = it }
        dstPort?.let { row["dst-port"] = it }
        inInterface?.let { row["in-interface"] = it }
        inInterfaceList?.let { row["in-interface-list"] = it }
        outInterface?.let { row["out-interface"] = it }
        return row
    }
}
