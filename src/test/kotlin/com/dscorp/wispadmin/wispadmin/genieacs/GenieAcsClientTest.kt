package com.dscorp.wispadmin.wispadmin.genieacs

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.dscorp.wispadmin.wispadmin.cpe.CpeWarnings
import com.dscorp.wispadmin.wispadmin.dto.CpeNetworkConfigRequest
import com.dscorp.wispadmin.wispadmin.genieacs.CpeDeviceOfflineException
import org.springframework.web.reactive.function.client.WebClient
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class GenieAcsClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GenieAcsClient
    private val recentInform = Instant.now().toString()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val webClient = WebClient.builder()
            .baseUrl(server.url("/").toString().trimEnd('/'))
            .build()
        client = GenieAcsClient(webClient)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `updateWifi sets SSID and KeyPassphrase on WLAN instances that already have a password`() {
        server.enqueue(deviceLookupResponse("00259E-EG8145V5-HWTC15F5CD86"))
        server.enqueue(wlanTreeResponse())
        server.enqueue(taskAcceptedResponse())

        client.updateWifi("HWTC15F5CD86", "GigaFiber-Casa", "clavewifi1")

        val lookup = server.takeRequest()
        assertEquals("GET", lookup.method)
        val decodedLookup = URLDecoder.decode(lookup.path, StandardCharsets.UTF_8)
        assertTrue(decodedLookup.contains("/devices/"))
        assertTrue(decodedLookup.contains("""{"_deviceId._SerialNumber":"HWTC15F5CD86"}"""))

        val wlanLookup = server.takeRequest()
        assertEquals("GET", wlanLookup.method)
        val decodedWlan = URLDecoder.decode(wlanLookup.path, StandardCharsets.UTF_8)
        assertTrue(decodedWlan.contains("WLANConfiguration"))

        val task = server.takeRequest()
        assertEquals("POST", task.method)
        assertTrue(task.path!!.contains("/devices/00259E-EG8145V5-HWTC15F5CD86/tasks"))
        val body = task.body.readUtf8()
        assertTrue(body.contains("setParameterValues"))
        assertTrue(body.contains("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID"))
        assertTrue(body.contains("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.KeyPassphrase"))
        assertTrue(body.contains("InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID"))
        assertTrue(body.contains("InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.KeyPassphrase"))
        assertTrue(body.contains("GigaFiber-Casa"))
        assertTrue(body.contains("clavewifi1"))
        assertTrue(!body.contains("PreSharedKey"))
        assertTrue(!body.contains("Device.WiFi"))
        assertTrue(!body.contains("WLANConfiguration.2.SSID"))
    }

    @Test
    fun `updateWifi finds V-SOL when ACS serial is not the PON SN`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "_id": "B46415-V2804AX15T-12345B46415F5E946",
                      "_lastInform": "$recentInform"
                    }]
                    """.trimIndent()
                )
        )
        server.enqueue(wlanTreeResponse())
        server.enqueue(taskAcceptedResponse())

        client.updateWifi("HWTC15F5E946", "OFICINA-TEST", "prueba1234")

        val lookup = server.takeRequest()
        val decodedPath = URLDecoder.decode(lookup.path, StandardCharsets.UTF_8)
        assertTrue(decodedPath.contains("\$or"))
        assertTrue(decodedPath.contains("""{"_deviceId._SerialNumber":"HWTC15F5E946"}"""))
        assertTrue(decodedPath.contains("15F5E946"))

        server.takeRequest()
        val task = server.takeRequest()
        assertEquals("POST", task.method)
        assertTrue(task.path!!.contains("/devices/B46415-V2804AX15T-12345B46415F5E946/tasks"))
        val body = task.body.readUtf8()
        assertTrue(body.contains("InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID"))
        assertTrue(body.contains("OFICINA-TEST"))
    }

    @Test
    fun `applyCpeConfiguration sets WAN IP gateway DNS and VLAN parameters`() {
        server.enqueue(deviceLookupResponse("B46415-V2804AX15T-12345B46415F5E946"))
        server.enqueue(wanTreeResponse())
        server.enqueue(taskAcceptedResponse())

        client.applyCpeConfiguration(
            sn = "HWTC15F5E946",
            network = com.dscorp.wispadmin.wispadmin.dto.CpeNetworkConfigRequest(
                ipAddress = "192.168.30.50",
                subnetMask = "255.255.255.0",
                gateway = "192.168.30.1",
                dnsPrimary = "8.8.8.8",
                dnsSecondary = "8.8.4.4",
                vlanId = 100,
            ),
            ssid = null,
            passphrase = null,
        )

        server.takeRequest()
        server.takeRequest()
        val task = server.takeRequest()
        val body = task.body.readUtf8()
        assertTrue(body.contains("ExternalIPAddress"))
        assertTrue(body.contains("192.168.30.50"))
        assertTrue(body.contains("DefaultGateway"))
        assertTrue(body.contains("192.168.30.1"))
        assertTrue(body.contains("DNSServers"))
        assertTrue(body.contains("8.8.8.8,8.8.4.4"))
        assertTrue(body.contains("X_VSOL_VLANID"))
        assertTrue(body.contains("100"))
        assertTrue(body.contains("AddressingType"))
    }

    @Test
    fun `applyCpeConfiguration skips WAN and still applies wifi when no writable WAN node exists`() {
        server.enqueue(deviceLookupResponse("B46415-V2804AX15T-12345B46415F5E946"))
        server.enqueue(emptyWanTreeResponse())
        server.enqueue(wlanTreeResponse())
        server.enqueue(taskAcceptedResponse())

        val result = client.applyCpeConfiguration(
            sn = "HWTC15F5E946",
            network = networkRequest(vlanId = 100),
            ssid = "GigaFiber-Casa",
            passphrase = "clavewifi1",
        )

        assertFalse(result.appliedNetwork)
        assertTrue(result.appliedWifi)
        assertEquals(listOf(CpeWarnings.WAN_NOT_WRITABLE), result.warnings)

        server.takeRequest()
        server.takeRequest()
        server.takeRequest()
        val task = server.takeRequest()
        val body = task.body.readUtf8()
        assertTrue(body.contains("KeyPassphrase"))
        assertTrue(!body.contains("ExternalIPAddress"))
    }

    @Test
    fun `applyCpeConfiguration applies IP settings and warns when VLAN is not writable`() {
        server.enqueue(deviceLookupResponse("B46415-V2804AX15T-12345B46415F5E946"))
        server.enqueue(wanTreeWithoutVlanResponse())
        server.enqueue(taskAcceptedResponse())

        val result = client.applyCpeConfiguration(
            sn = "HWTC15F5E946",
            network = networkRequest(vlanId = 100),
            ssid = null,
            passphrase = null,
        )

        assertTrue(result.appliedNetwork)
        assertFalse(result.appliedWifi)
        assertEquals(listOf(CpeWarnings.VLAN_NOT_WRITABLE), result.warnings)

        server.takeRequest()
        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("ExternalIPAddress"))
        assertTrue(!body.contains("VLANID"))
    }

    @Test
    fun `applyCpeConfiguration enqueues no task when nothing can be written`() {
        server.enqueue(deviceLookupResponse("B46415-V2804AX15T-12345B46415F5E946"))
        server.enqueue(emptyWanTreeResponse())

        val result = client.applyCpeConfiguration(
            sn = "HWTC15F5E946",
            network = networkRequest(vlanId = null),
            ssid = null,
            passphrase = null,
        )

        assertFalse(result.appliedNetwork)
        assertFalse(result.appliedWifi)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `findDeviceDescriptor exposes vendor and model parsed from the ACS device id`() {
        server.enqueue(deviceLookupResponse("B46415-V2804AX15T-12345B46415F5E946"))

        val descriptor = client.findDeviceDescriptor("HWTC15F5E946")

        assertEquals("B46415-V2804AX15T-12345B46415F5E946", descriptor?.deviceId)
        assertEquals("B46415", descriptor?.vendor)
        assertEquals("V2804AX15T", descriptor?.model)
        assertTrue(descriptor?.reachable == true)
    }

    @Test
    fun `findDeviceDescriptor marks stale devices as unreachable`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "_id": "00259E-EG8145V5-HWTC15F5CD86",
                      "_lastInform": "2020-01-01T00:00:00.000Z"
                    }]
                    """.trimIndent()
                )
        )

        val descriptor = client.findDeviceDescriptor("HWTC15F5CD86")

        assertEquals(false, descriptor?.reachable)
        assertEquals("2020-01-01T00:00:00.000Z", descriptor?.lastInform)
    }

    @Test
    fun `updateWifi throws when lastInform is stale`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "_id": "00259E-EG8145V5-HWTC15F5CD86",
                      "_lastInform": "2020-01-01T00:00:00.000Z"
                    }]
                    """.trimIndent()
                )
        )

        assertThrows<CpeDeviceOfflineException> {
            client.updateWifi("HWTC15F5CD86", "Red", "password1")
        }
    }

    @Test
    fun `updateWifi throws when GenieACS has no device for the serial`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        assertThrows<GenieAcsDeviceNotFoundException> {
            client.updateWifi("MISSINGSN", "Red", "password1")
        }
    }

    @Test
    fun `getLastInform returns ACS timestamp for the serial`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "_id": "00259E-EG8145V5-HWTC15F5CD86",
                      "_lastInform": "2026-08-17T14:00:00.000Z"
                    }]
                    """.trimIndent()
                )
        )

        val lastInform = client.getLastInform("HWTC15F5CD86")

        assertEquals("2026-08-17T14:00:00.000Z", lastInform)
    }

    @Test
    fun `getLastInform returns null when device is missing`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        assertNull(client.getLastInform("UNKNOWN"))
    }

    private fun deviceLookupResponse(deviceId: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                [{
                  "_id": "$deviceId",
                  "_lastInform": "$recentInform"
                }]
                """.trimIndent()
            )

    private fun wanTreeResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                [{
                  "InternetGatewayDevice": {
                    "WANDevice": {
                      "1": {
                        "WANConnectionDevice": {
                          "1": {
                            "WANIPConnection": {
                              "1": {
                                "ExternalIPAddress": {
                                  "_object": "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress",
                                  "_value": "192.168.30.199",
                                  "_writable": true
                                },
                                "SubnetMask": {"_value": "255.255.255.0", "_writable": true},
                                "DefaultGateway": {"_value": "192.168.30.1", "_writable": true},
                                "DNSServers": {"_value": "8.8.8.8,8.8.4.4", "_writable": true},
                                "AddressingType": {"_value": "Static", "_writable": true}
                              }
                            },
                            "X_VSOL_VLANID": {
                              "_object": "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.X_VSOL_VLANID",
                              "_value": "100",
                              "_writable": true
                            }
                          }
                        }
                      }
                    }
                  }
                }]
                """.trimIndent()
            )

    private fun emptyWanTreeResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""[{"InternetGatewayDevice": {"WANDevice": {}}}]""")

    private fun wanTreeWithoutVlanResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                [{
                  "InternetGatewayDevice": {
                    "WANDevice": {
                      "1": {
                        "WANConnectionDevice": {
                          "1": {
                            "WANIPConnection": {
                              "1": {
                                "ExternalIPAddress": {
                                  "_object": "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress",
                                  "_value": "192.168.30.199",
                                  "_writable": true
                                },
                                "SubnetMask": {"_value": "255.255.255.0", "_writable": true},
                                "DefaultGateway": {"_value": "192.168.30.1", "_writable": true},
                                "DNSServers": {"_value": "8.8.8.8", "_writable": true},
                                "AddressingType": {"_value": "Static", "_writable": true}
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }]
                """.trimIndent()
            )

    private fun networkRequest(vlanId: Int?) = CpeNetworkConfigRequest(
        ipAddress = "192.168.30.50",
        subnetMask = "255.255.255.0",
        gateway = "192.168.30.1",
        dnsPrimary = "8.8.8.8",
        dnsSecondary = "8.8.4.4",
        vlanId = vlanId,
    )

    private fun wlanTreeResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                [{
                  "InternetGatewayDevice": {
                    "LANDevice": {
                      "1": {
                        "WLANConfiguration": {
                          "1": {
                            "SSID": {"_value": "OFICINA _5G", "_writable": true},
                            "KeyPassphrase": {"_value": "12345678", "_writable": true}
                          },
                          "2": {
                            "SSID": {"_value": "AP-1", "_writable": true},
                            "KeyPassphrase": {"_value": "", "_writable": true}
                          },
                          "5": {
                            "SSID": {"_value": "OFICINA", "_writable": true},
                            "KeyPassphrase": {"_value": "88888888", "_writable": true}
                          }
                        }
                      }
                    }
                  }
                }]
                """.trimIndent()
            )

    private fun taskAcceptedResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""{"_id":"task-1","name":"setParameterValues"}""")
}
