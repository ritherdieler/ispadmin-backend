package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

class GenieAcsClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GenieAcsClient
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val properties = GenieAcsProperties().apply {
            nbiBaseUrl = server.url("/").toString().trimEnd('/')
            taskTimeoutMs = 2000
            connectTimeoutMs = 2000
        }
        val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(2000)
            setReadTimeout(2000)
        })
        client = GenieAcsClient(properties, objectMapper, restTemplate)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listDevices parses projection payload`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "_id":"B46415-V2804AX15T-12345B4641531C0B6",
                      "_lastInform":"2026-08-20T00:00:00.000Z",
                      "_lastBoot":"2026-08-20T00:00:00.000Z",
                      "_deviceId":{
                        "_SerialNumber":"12345B4641531C0B6",
                        "_ProductClass":"V2804AX15T",
                        "_Manufacturer":"VSOL",
                        "_OUI":"B46415",
                        "_SoftwareVersion":"V1.0",
                        "_HardwareVersion":"V1.1"
                      },
                      "InternetGatewayDevice":{
                        "ManagementServer":{
                          "ConnectionRequestURL":{"_value":"http://192.168.123.4:7547/tr069"}
                        }
                      }
                    }]
                    """.trimIndent()
                )
        )

        val devices = client.listDevices()

        assertEquals(1, devices.size)
        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", devices.first().id)
        assertEquals("12345B4641531C0B6", devices.first().serialNumber)
        assertEquals("V2804AX15T", devices.first().productClass)
        assertEquals("VSOL", devices.first().manufacturer)
        assertEquals("B46415", devices.first().oui)
        assertEquals("V1.0", devices.first().softwareVersion)
        assertEquals("V1.1", devices.first().hardwareVersion)
        assertEquals("http://192.168.123.4:7547/tr069", devices.first().connectionRequestUrl)
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/devices/"))
        assertTrue(request.path!!.contains("projection="))
    }

    @Test
    fun `findDeviceBySerialSuffix filters in memory`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"_id":"A-XXXXXX31C0B6","_deviceId":{"_SerialNumber":"XXXXXX31C0B6","_ProductClass":"V2804AX15T"}},
                      {"_id":"B-OTHER","_deviceId":{"_SerialNumber":"OTHER999999","_ProductClass":"X"}}
                    ]
                    """.trimIndent()
                )
        )

        val matches = client.findDeviceBySerialSuffix("31C0B6")
        assertEquals(1, matches.size)
        assertEquals("A-XXXXXX31C0B6", matches.first().id)
    }

    @Test
    fun `setParameterValues posts task with connection_request`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"_id":"task-1"}""")
        )

        val result = client.setParameterValues(
            deviceId = "B46415-V2804AX15T-12345B4641531C0B6",
            values = listOf(
                Tr069ParameterValue(
                    path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID",
                    value = "acs2g",
                    type = "xsd:string",
                )
            ),
            connectionRequest = true,
        )

        assertTrue(result.accepted)
        assertFalse(result.connectionRequestFailed)
        assertEquals("task-1", result.taskId)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/tasks"))
        assertTrue(request.path!!.contains("connection_request"))
        assertTrue(request.body.readUtf8().contains("setParameterValues"))
    }

    @Test
    fun `setParameterValues detects Incorrect connection request credentials`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"fault":"Incorrect connection request credentials"}""")
        )

        val result = client.setParameterValues(
            deviceId = "dev-1",
            values = listOf(
                Tr069ParameterValue("InternetGatewayDevice.x", "1", "xsd:string")
            ),
            connectionRequest = true,
        )

        assertTrue(result.accepted)
        assertTrue(result.connectionRequestFailed)
    }

    @Test
    fun `getParameterValues posts task without connection_request by default`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"_id":"task-gpv"}""")
        )

        val result = client.getParameterValues(
            deviceId = "dev-1",
            parameterNames = listOf("InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID"),
        )

        assertTrue(result.accepted)
        val request = server.takeRequest()
        assertFalse(request.path!!.contains("connection_request"))
        assertTrue(request.body.readUtf8().contains("getParameterValues"))
    }
}
