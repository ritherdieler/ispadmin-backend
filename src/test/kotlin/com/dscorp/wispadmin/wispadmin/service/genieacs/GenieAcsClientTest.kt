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

        assertFalse(result.accepted)
        assertTrue(result.connectionRequestFailed)
    }

    @Test
    fun `toResponseMessage returns HTTP status and GenieACS body like response log`() {
        val body = """{"name":"setParameterValues","_id":"task-abc"}"""
        val result = GenieAcsTaskResult(
            statusCode = 202,
            body = body,
            accepted = false,
        )

        assertEquals(
            "HTTP 202\n$body",
            result.toResponseMessage(),
        )
    }

    @Test
    fun `findFaultBodyForTask returns fault JSON when channel matches task id`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{
                      "device":"dev-1",
                      "channel":"task_task-abc",
                      "code":"cwmp.9003",
                      "message":"Invalid arguments",
                      "detail":{"faultString":"Invalid arguments"}
                    }]
                    """.trimIndent()
                )
        )

        val fault = client.findFaultBodyForTask("dev-1", "task-abc")

        assertTrue(fault!!.contains("Invalid arguments"))
        assertTrue(fault.contains("cwmp.9003"))
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

    @Test
    fun `purgeDeviceQueue deletes pending tasks and faults for device`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"_id":"task-old","device":"dev-1","name":"setParameterValues"},
                      {"_id":"task-old-2","device":"dev-1","name":"getParameterValues"}
                    ]
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [{"_id":"fault-old","device":"dev-1","channel":"task_task-old"}]
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200))

        val result = client.purgeDeviceQueue("dev-1")

        assertEquals(2, result.tasksDeleted)
        assertEquals(1, result.faultsDeleted)
        val listTasks = server.takeRequest()
        assertEquals("GET", listTasks.method)
        assertTrue(listTasks.path!!.contains("/tasks/"))
        val deleteTask1 = server.takeRequest()
        assertEquals("DELETE", deleteTask1.method)
        assertTrue(deleteTask1.path!!.contains("task-old"))
        val deleteTask2 = server.takeRequest()
        assertEquals("DELETE", deleteTask2.method)
        assertTrue(deleteTask2.path!!.contains("task-old-2"))
        val listFaults = server.takeRequest()
        assertEquals("GET", listFaults.method)
        assertTrue(listFaults.path!!.contains("/faults/"))
        val deleteFault = server.takeRequest()
        assertEquals("DELETE", deleteFault.method)
        assertTrue(deleteFault.path!!.contains("fault-old"))
    }

    @Test
    fun `purgeDeviceQueue returns zero when queue is empty`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        val result = client.purgeDeviceQueue("dev-1")

        assertEquals(0, result.tasksDeleted)
        assertEquals(0, result.faultsDeleted)
    }

    @Test
    fun `addObject posts task with objectName and connection_request`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"_id":"task-add"}""")
        )

        val result = client.addObject(
            deviceId = "B46415-V2804AX15T-12345B4641531C0B6",
            objectName = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice",
            connectionRequest = true,
        )

        assertTrue(result.accepted)
        assertEquals("task-add", result.taskId)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("connection_request"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"addObject\""), body)
        assertTrue(body.contains("WANConnectionDevice"), body)
    }

    @Test
    fun `listWanConnectionDeviceIndices returns all WCD slots including empty ones`() {
        server.enqueue(wanTreeBody("""{"1":{"WANIPConnection":{"1":{}}},"2":{}}"""))

        assertEquals(listOf(1, 2), client.listWanConnectionDeviceIndices("dev-1"))
    }

    @Test
    fun `hasWanIpConnection is true only when WANIPConnection 1 exists`() {
        server.enqueue(wanTreeBody("""{"1":{"WANIPConnection":{"1":{}}},"2":{}}"""))
        server.enqueue(wanTreeBody("""{"1":{"WANIPConnection":{"1":{}}},"2":{}}"""))

        assertTrue(client.hasWanIpConnection("dev-1", 1))
        assertFalse(client.hasWanIpConnection("dev-1", 2))
    }

    private fun wanTreeBody(wanConnectionDeviceJson: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"dev-1",
              "InternetGatewayDevice":{
                "WANDevice":{"1":{
                  "WANConnectionDevice":$wanConnectionDeviceJson
                }}
              }
            }]
            """.trimIndent()
        )

    @Test
    fun `formatTaskError includes HTTP status and GenieACS detail`() {
        val formatted = GenieAcsClient.formatTaskError(
            GenieAcsTaskResult(
                statusCode = 400,
                body = """{"detail":"missing or invalid resource identifier","error":400,"message":"Bad Request"}""",
                accepted = false,
            )
        )

        assertEquals(
            "GenieACS HTTP 400: missing or invalid resource identifier",
            formatted,
        )
    }

    @Test
    fun `formatTaskError expands cwmp setParameterValuesFault detail`() {
        val formatted = GenieAcsClient.formatTaskError(
            GenieAcsTaskResult(
                statusCode = 202,
                body = """
                {
                  "code":"cwmp.9003",
                  "message":"Invalid arguments",
                  "detail":{
                    "faultString":"Invalid arguments",
                    "setParameterValuesFault":[
                      {
                        "parameterName":"InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress",
                        "faultCode":"9001",
                        "faultString":"Request denied"
                      }
                    ]
                  }
                }
                """.trimIndent(),
                accepted = false,
            )
        )

        assertTrue(formatted.contains("ExternalIPAddress"))
        assertTrue(formatted.contains("Request denied"))
    }
}
