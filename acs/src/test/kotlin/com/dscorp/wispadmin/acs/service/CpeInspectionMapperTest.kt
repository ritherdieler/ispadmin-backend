package com.dscorp.wispadmin.acs.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CpeInspectionMapperTest {
    private val json = ObjectMapper()
    private val mapper = CpeInspectionMapper()

    @Test
    fun `summary identifies management WAN by connection request IP and model-specific WiFi bands`() {
        val zte = device("F6600R", "192.168.254.196", "192.168.250.10", 1, 5)
        val vsol = device("V2804AX15T", "10.20.0.158", "10.64.47.209", 5, 1)

        val zteSummary = mapper.summary(zte)
        val vsolSummary = mapper.summary(vsol)

        assertEquals("192.168.254.196", zteSummary.managementWan?.ipAddress)
        assertEquals("192.168.250.10", zteSummary.internetWan?.ipAddress)
        assertEquals("ZTE-24", zteSummary.ssid24)
        assertEquals("ZTE-5", zteSummary.ssid5)
        assertEquals("10.20.0.158", vsolSummary.managementWan?.ipAddress)
        assertEquals("10.64.47.209", vsolSummary.internetWan?.ipAddress)
        assertEquals("VSOL-24", vsolSummary.ssid24)
        assertEquals("VSOL-5", vsolSummary.ssid5)
    }

    @Test
    fun `summary falls back to WAN service labels when request URL is translated`() {
        val device = json.readTree("""{
          "_id":"device-1", "_deviceId":{"_ProductClass":"F6600R"},
          "InternetGatewayDevice":{
            "ManagementServer":{"ConnectionRequestURL":{"_value":"http://gateway.example.net:7547/"}},
            "WANDevice":{"1":{"WANConnectionDevice":{
              "1":{"WANIPConnection":{"1":{"ExternalIPAddress":{"_value":"10.0.0.2"},"ServiceList":{"_value":"TR069"}}}},
              "2":{"WANIPConnection":{"1":{"ExternalIPAddress":{"_value":"198.51.100.2"},"ServiceList":{"_value":"INTERNET"},"ConnectionStatus":{"_value":"Connected"}}}}
            }}}
          }
        }""")
        val summary = mapper.summary(device)
        assertEquals("10.0.0.2", summary.managementWan?.ipAddress)
        assertEquals("198.51.100.2", summary.internetWan?.ipAddress)
    }

    @Test
    fun `tree reports missing values and metadata while redacting secrets`() {
        val device = json.readTree("""{
          "_id":"device-1", "_lastInform":"2026-10-02T07:00:00Z",
          "InternetGatewayDevice":{"_object":true,
            "LANDevice":{"_object":true,"1":{"_object":true,"WLANConfiguration":{"_object":true,
              "1":{"_object":true,
                "SSID":{"_value":"Home","_type":"xsd:string","_writable":true,"_timestamp":"2026-10-02T06:59:00Z"},
                "KeyPassphrase":{"_value":"do-not-return","_type":"xsd:string","_writable":true},
                "Channel":{"_type":"xsd:unsignedInt","_writable":true}
              }
            }}}}
          }
        }""")

        val branch = mapper.tree(device, "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1", null)
        val byName = branch.entries.associateBy { it.name }
        assertEquals("Home", byName["SSID"]?.value)
        assertEquals("2026-10-02T06:59:00Z", byName["SSID"]?.observedAt)
        assertNull(byName["KeyPassphrase"]?.value)
        assertEquals(true, byName["KeyPassphrase"]?.redacted)
        assertEquals(false, byName["Channel"]?.hasValue)
        assertEquals("xsd:unsignedInt", byName["Channel"]?.type)
        assertEquals(true, byName["Channel"]?.writable)
        assertTrue(mapper.tree(device, null, "ssid").entries.any { it.path.endsWith(".SSID") })
    }

    @Test
    fun `fault projection exposes codes and paths without raw diagnostic payload`() {
        val raw = json.readTree("""{
          "_id":"device-1:task_42", "device":"device-1", "channel":"task_42",
          "timestamp":"2026-10-02T07:00:00Z", "retries":2,
          "fault":{"code":"cwmp.9003","message":"password=secret-value",
            "detail":{"setParameterValuesFault":[{"parameterName":"InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID","faultCode":"9007","faultString":"Invalid parameter value"}]}}
        }""")

        val fault = mapper.fault(raw)
        assertEquals("42", fault.taskId)
        assertEquals("cwmp.9003", fault.code)
        assertEquals(listOf("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID"), fault.parameters)
        assertEquals(2, fault.retries)
        assertFalse(fault.description.contains("secret-value"))
    }

    @Test
    fun `fault projection discards unexpected free text in diagnostic identifiers`() {
        val raw = json.readTree("""{
          "_id":"device-1:inform", "device":"device-1", "channel":"password=secret-value",
          "timestamp":"password=secret-value", "fault":{"code":"cwmp.9002"}
        }""")
        val fault = mapper.fault(raw)
        assertNull(fault.channel)
        assertNull(fault.taskId)
        assertNull(fault.occurredAt)
        assertFalse(fault.toString().contains("secret-value"))
    }

    private fun device(model: String, managementIp: String, internetIp: String, wifi24: Int, wifi5: Int) =
        json.readTree("""{
          "_id":"device-1", "_lastInform":"2026-10-02T07:00:00Z", "_deviceId":{"_ProductClass":"$model"},
          "InternetGatewayDevice":{
            "ManagementServer":{"ConnectionRequestURL":{"_value":"http://$managementIp:7547/tr069"}},
            "DeviceInfo":{"SoftwareVersion":{"_value":"V1"}},
            "WANDevice":{"1":{"WANConnectionDevice":{
              "1":{"WANIPConnection":{"1":{"ExternalIPAddress":{"_value":"$managementIp"}}}},
              "2":{"WANPPPConnection":{"1":{"ExternalIPAddress":{"_value":"$internetIp"},"ConnectionStatus":{"_value":"Connected"}}}}
            }}},
            "LANDevice":{"1":{"WLANConfiguration":{
              "$wifi24":{"SSID":{"_value":"${if (model == "F6600R") "ZTE" else "VSOL"}-24"}},
              "$wifi5":{"SSID":{"_value":"${if (model == "F6600R") "ZTE" else "VSOL"}-5"}}
            }}}
          }
        }""")
}
