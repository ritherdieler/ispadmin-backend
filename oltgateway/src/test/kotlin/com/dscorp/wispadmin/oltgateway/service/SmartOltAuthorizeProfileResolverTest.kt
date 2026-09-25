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
    fun `Generic_1 vlan 100 resuelve line 30 srv 13 y VLAN 1000 de gestion`() {
        val resolved = resolver().resolve(
            customProfile = "Generic_1",
            vlan = 100,
            name = "HOMOLOG-GW-TEST",
            zone = "Zone 1",
            sn = "ZTEGDC47BFFD",
            at = LocalDate.of(2026, 9, 2).atStartOfDay().toInstant(ZoneOffset.UTC),
        )

        assertEquals(30, resolved.lineProfileId)
        assertEquals(13, resolved.serviceProfileId)
        assertEquals(1000, resolved.mgmtVlan)
        assertEquals(2, resolved.mgmtGemport)
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
    fun `sin binding en vlan 100 igual fuerza line 30 y VLAN 1000`() {
        val resolved = resolver(bindings = "").resolve(
            customProfile = "Unknown",
            vlan = 100,
            name = "solo-nombre",
            zone = "Zone 1",
            sn = "SN1",
            at = LocalDate.of(2026, 9, 2).atStartOfDay().toInstant(ZoneOffset.UTC),
        )

        assertEquals(30, resolved.lineProfileId)
        assertEquals(10, resolved.serviceProfileId)
        assertEquals(1000, resolved.mgmtVlan)
        assertEquals("solo-nombre_zone_Zone 1_authd_20260902", resolved.description)
    }

    @Test
    fun `VSOL lab 0031C0B6 usa lineprofile 30 y VLAN 1000 de gestion ACS`() {
        val resolved = resolver().resolve(
            customProfile = "Generic_1",
            vlan = 100,
            name = "EeeFiber",
            zone = "Zone 1",
            sn = "VSOL0031C0B6",
            at = LocalDate.of(2026, 9, 11).atStartOfDay().toInstant(ZoneOffset.UTC),
        )

        assertEquals(30, resolved.lineProfileId)
        assertEquals(13, resolved.serviceProfileId)
        assertEquals(1000, resolved.mgmtVlan)
        assertEquals(2, resolved.mgmtGemport)
    }

    @Test
    fun `serial hex de la VSOL lab tambien lleva VLAN 1000 de gestion`() {
        val resolved = resolver().resolve(
            customProfile = "Generic_1",
            vlan = 100,
            name = "lab",
            zone = "Zone 1",
            sn = "12345B4641531C0B6",
        )

        assertEquals(30, resolved.lineProfileId)
        assertEquals(1000, resolved.mgmtVlan)
    }

    @Test
    fun `VSOL de cliente en vlan 100 tambien lleva line 30 y VLAN 1000`() {
        val resolved = resolver().resolve(
            customProfile = "Generic_1",
            vlan = 100,
            name = "cliente",
            zone = "Zone 1",
            sn = "VSOL003217B6",
        )

        assertEquals(30, resolved.lineProfileId)
        assertEquals(1000, resolved.mgmtVlan)
        assertEquals(2, resolved.mgmtGemport)
    }

    @Test
    fun `vlan 1 no agrega VLAN 1000 de gestion`() {
        val resolved = resolver().resolve(
            customProfile = "Generic_1",
            vlan = 1,
            name = "cliente",
            zone = "Zone 1",
            sn = "VSOL003217B6",
        )

        assertEquals(3, resolved.lineProfileId)
        assertEquals(null, resolved.mgmtVlan)
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
