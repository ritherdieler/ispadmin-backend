package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SmartOltAuthorizeProfileResolverTest {

    private fun resolver(
        bindings: String = "Generic_1:1=3:2,Generic_1:100=6:13",
        defaultLine: Int = 10,
        defaultSrv: Int = 10,
    ): SmartOltAuthorizeProfileResolver {
        val props = OltGatewayProperties().apply {
            writes.defaultLineProfileId = defaultLine
            writes.defaultServiceProfileId = defaultSrv
            writes.customProfileBindings = bindings
        }
        return SmartOltAuthorizeProfileResolver(props)
    }

    @Test
    fun `Generic_1 vlan 100 resuelve line 6 y srv 13`() {
        val resolved = resolver().resolve(
            customProfile = "Generic_1",
            vlan = 100,
            name = "HOMOLOG-GW-TEST",
            zone = "Zone 1",
            sn = "ZTEGDC47BFFD",
            at = LocalDate.of(2026, 9, 2).atStartOfDay().toInstant(ZoneOffset.UTC),
        )

        assertEquals(6, resolved.lineProfileId)
        assertEquals(13, resolved.serviceProfileId)
        assertEquals("HOMOLOG-GW-TEST_zone_Zone 1_authd_20260902", resolved.description)
    }

    @Test
    fun `Generic_1 vlan 1 resuelve line 3 y srv 2`() {
        val resolved = resolver().resolve(
            customProfile = "Generic_1",
            vlan = 1,
            name = "cliente",
            zone = "Zone 1",
            sn = "SN1",
            at = LocalDate.of(2026, 9, 2).atStartOfDay().toInstant(ZoneOffset.UTC),
        )

        assertEquals(3, resolved.lineProfileId)
        assertEquals(2, resolved.serviceProfileId)
    }

    @Test
    fun `sin binding usa defaults y desc con name`() {
        val resolved = resolver(bindings = "").resolve(
            customProfile = "Unknown",
            vlan = 100,
            name = "solo-nombre",
            zone = "Zone 1",
            sn = "SN1",
            at = LocalDate.of(2026, 9, 2).atStartOfDay().toInstant(ZoneOffset.UTC),
        )

        assertEquals(10, resolved.lineProfileId)
        assertEquals(10, resolved.serviceProfileId)
        assertEquals("solo-nombre_zone_Zone 1_authd_20260902", resolved.description)
    }

    @Test
    fun `name vacio usa sn en description`() {
        val resolved = resolver().resolve(
            customProfile = "Generic_1",
            vlan = 100,
            name = "  ",
            zone = "Zone 1",
            sn = "ZTEGDC47BFFD",
            at = LocalDate.of(2026, 9, 2).atStartOfDay().toInstant(ZoneOffset.UTC),
        )

        assertEquals("ZTEGDC47BFFD_zone_Zone 1_authd_20260902", resolved.description)
    }
}
