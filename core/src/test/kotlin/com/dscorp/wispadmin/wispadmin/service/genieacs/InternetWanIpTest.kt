package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InternetWanIpTest {

    private val mapper = ObjectMapper()

    @Test
    fun `keeps internet ExternalIPAddress and drops TR-069 pools gateway and mask`() {
        val node = mapper.readTree(
            """
            {
              "WANDevice":{"1":{"WANConnectionDevice":{
                "1":{"WANIPConnection":{"1":{
                  "ExternalIPAddress":{"_value":"192.168.211.157"},
                  "SubnetMask":{"_value":"255.255.255.0"},
                  "DefaultGateway":{"_value":"192.168.211.1"}
                }}},
                "2":{"WANIPConnection":{"1":{
                  "ExternalIPAddress":{"_value":"192.168.255.204"}
                }}}
              }}}
            }
            """.trimIndent()
        )

        val ips = InternetWanIp.internetHosts(node, crHost = "192.168.255.204")

        assertEquals(listOf("192.168.211.157"), ips)
    }

    @Test
    fun `drops mgmt VLAN 1000 and zero addresses`() {
        val node = mapper.readTree(
            """
            {"WANIPConnection":{"1":{"ExternalIPAddress":{"_value":"10.20.0.158"}},
             "2":{"ExternalIPAddress":{"_value":"0.0.0.0"}},
             "3":{"ExternalIPAddress":{"_value":"10.64.0.36"}}}}
            """.trimIndent()
        )

        val ips = InternetWanIp.internetHosts(node, crHost = "10.20.0.158")

        assertEquals(listOf("10.64.0.36"), ips)
        assertTrue(InternetWanIp.isMgmt("192.168.252.10"))
        assertFalse(InternetWanIp.isMgmt("192.168.30.220"))
    }
}
