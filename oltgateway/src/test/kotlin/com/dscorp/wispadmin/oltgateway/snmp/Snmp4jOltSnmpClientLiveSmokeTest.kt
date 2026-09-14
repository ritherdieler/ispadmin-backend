package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Live smoke against MA5608T. Skipped unless:
 *   OLT_SNMP_LIVE=true
 *   OLT_GATEWAY_SNMP_RO_COMMUNITY=&lt;ro&gt;
 *
 * Does not run optical full walk (slow); use CLI or POST /admin/sync/signal for that.
 */
@Tag("live")
class Snmp4jOltSnmpClientLiveSmokeTest {

    @Test
    fun `cliente SNMP lista ONUs configuradas`() {
        assumeTrue(System.getenv("OLT_SNMP_LIVE") == "true") { "set OLT_SNMP_LIVE=true" }
        val community = System.getenv("OLT_GATEWAY_SNMP_RO_COMMUNITY").orEmpty()
        assumeTrue(community.isNotBlank()) { "set OLT_GATEWAY_SNMP_RO_COMMUNITY" }

        val props = OltGatewayProperties().apply {
            host = System.getenv("OLT_GATEWAY_HOST") ?: "10.11.104.2"
            snmp.enabled = true
            snmp.port = (System.getenv("OLT_GATEWAY_SNMP_PORT") ?: "161").toInt()
            snmp.roCommunity = community
            snmp.timeoutMs = 8000
            snmp.retries = 1
            snmp.maxRepetitions = 25
        }
        val client = Snmp4jOltSnmpClient(props)

        val sysOid = client.probeSysObjectId()?.trimStart('.')
        assertTrue(
            sysOid == HuaweiGponSnmpOids.ENTERPRISE_SYS_OBJECT ||
                sysOid?.endsWith("2011.2.248") == true,
            "sysObjectID=$sysOid"
        )

        val onus = client.listConfiguredOnus()
        assertTrue(onus.size >= 100, "expected hundreds of ONUs, got ${onus.size}")
        val sample = onus.first()
        assertTrue(sample.sn.length >= 8, "sn=${sample.sn}")
        assertTrue(sample.runState == "online" || sample.runState == "offline", "runState=${sample.runState}")

        val autofind = client.listAutofind()
        // may be empty; just ensure call works
        assertTrue(autofind.size >= 0)

        println(
            "LIVE SNMP OK host=${props.host} onus=${onus.size} " +
                "online=${onus.count { it.runState == "online" }} " +
                "offline=${onus.count { it.runState == "offline" }} " +
                "autofind=${autofind.size} sample=${sample.slot}/${sample.port}/${sample.ontId} ${sample.sn}"
        )
    }
}
