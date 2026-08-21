package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.util.concurrent.atomic.AtomicLong

class Tr069ProvisioningServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: Tr069ProvisioningService
    private val now = AtomicLong(0L)
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        now.set(0L)
        val properties = GenieAcsProperties().apply {
            enabled = true
            nbiBaseUrl = server.url("/").toString().trimEnd('/')
            waitTimeoutMs = 20_000
            pollIntervalMs = 5_000
            taskTimeoutMs = 2_000
            connectTimeoutMs = 2_000
            defaultDns = "8.8.8.8,8.8.4.4"
            wanVlanId = 1
        }
        val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(2000)
            setReadTimeout(2000)
        })
        val client = GenieAcsClient(properties, objectMapper, restTemplate)
        service = Tr069ProvisioningService(client, properties).withTimeControls(
            clock = { now.get() },
            sleeper = { ms -> now.addAndGet(ms) },
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `device appears on second poll then COMPLETE`() {
        server.enqueue(emptyDevices())
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(index = 1))
        server.enqueue(deviceWanIpRead(wanIp = "192.168.123.4"))
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted()) // getParameterValues
        server.enqueue(emptyFaults())
        server.enqueue(deviceWithSsids("acs2g", "acs5g")) // SSID 2.4
        server.enqueue(deviceWithSsids("acs2g", "acs5g")) // SSID 5

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", outcome.deviceId)
        assertTrue(outcome.message!!.contains("automáticamente", ignoreCase = true))
        assertEquals("31C0B6", outcome.acsSnapshot?.serialSuffix)
        assertEquals("V2804AX15T", outcome.acsSnapshot?.productClass)
        assertEquals("task-1", outcome.acsSnapshot?.lastTaskId)
        assertEquals("accepted", outcome.acsSnapshot?.lastTaskStatus)
        assertEquals("acs2g", outcome.acsSnapshot?.ssid24)
        assertEquals("192.168.123.4", outcome.acsSnapshot?.wanIpCache)
    }

    @Test
    fun `provision purges stale queue before setParameterValues`() {
        server.enqueue(deviceList())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """[{"_id":"task-stale","device":"B46415-V2804AX15T-12345B4641531C0B6","name":"setParameterValues"}]"""
                )
        )
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(emptyFaults())
        server.enqueue(wanConnectionTree(index = 1))
        server.enqueue(deviceWanIpRead(wanIp = "192.168.123.4"))
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        server.takeRequest() // listDevices
        val listTasks = server.takeRequest()
        assertTrue(listTasks.path!!.contains("/tasks/"))
        val deleteStale = server.takeRequest()
        assertEquals("DELETE", deleteStale.method)
        assertTrue(deleteStale.path!!.contains("task-stale"))
        server.takeRequest() // faults list
    }

    @Test
    fun `setParameterValues uses wanVlanId from request not global property`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(index = 1))
        server.enqueue(deviceWanIpRead(wanIp = "192.168.123.4"))
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))

        val outcome = service.provision(sampleRequest().copy(wanVlanId = 100))

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        // 1=listDevices, 2=tasks purge, 3=faults purge, 4=wan tree, 5=wan ip, 6=setParameterValues
        server.takeRequest()
        server.takeRequest()
        server.takeRequest()
        server.takeRequest()
        server.takeRequest()
        val setBody = server.takeRequest().body.readUtf8()
        assertTrue(setBody.contains("\"100\""), "expected VLAN 100 in SPV payload: $setBody")
        assertTrue(setBody.contains("X_CT-COM_VLANIDMark"), setBody)
    }

    @Test
    fun `device never appears returns MANUAL_REQUIRED`() {
        repeat(5) { server.enqueue(emptyDevices()) }

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertTrue(outcome.message!!.contains("no contactó", ignoreCase = true))
    }

    @Test
    fun `setParameterValues rejected includes GenieACS HTTP status and body in message`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(index = 1))
        server.enqueue(deviceWanIpRead(wanIp = "192.168.123.4"))
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """{"detail":"missing or invalid resource identifier","error":400,"message":"Bad Request"}"""
                )
        )

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertTrue(outcome.message!!.contains("GenieACS HTTP 400"))
        assertTrue(outcome.message!!.contains("missing or invalid resource identifier"))
        assertEquals(outcome.message, outcome.error)
    }

    @Test
    fun `connection request credentials error returns MANUAL_REQUIRED`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(index = 1))
        server.enqueue(deviceWanIpRead(wanIp = "192.168.123.4"))
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"fault":"Incorrect connection request credentials"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"fault":"Incorrect connection request credentials"}""")
        )

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertEquals(outcome.message, outcome.error)
        assertTrue(outcome.message!!.startsWith("HTTP 202"))
        assertTrue(outcome.message!!.contains("Incorrect connection request credentials"))
    }

    @Test
    fun `task fault after HTTP 202 returns GenieACS response message to app`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(index = 1))
        server.enqueue(deviceWanIpRead(wanIp = "192.168.123.4"))
        server.enqueue(taskAccepted())
        server.enqueue(taskFault(taskId = "task-1"))

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertEquals(outcome.message, outcome.error)
        assertTrue(outcome.message!!.contains("Request denied"))
        assertTrue(outcome.message!!.contains("ExternalIPAddress"))
    }

    @Test
    fun `SSID verification failure returns human timeout message`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(index = 1))
        server.enqueue(deviceWanIpRead(wanIp = "192.168.123.4"))
        val taskBody = """{"name":"setParameterValues","_id":"task-1"}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .setBody(taskBody)
        )
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted()) // getParameterValues
        repeat(4) { server.enqueue(deviceWithSsids("wrong24", "wrong5")) }
        repeat(4) { server.enqueue(emptyFaults()) }

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertEquals(outcome.message, outcome.error)
        assertTrue(outcome.message!!.contains("SSID", ignoreCase = true))
        assertTrue(outcome.message!!.contains("tiempo de espera", ignoreCase = true))
    }

    @Test
    fun `staging WAN 255x runs dhcp prep SPV before prod static SPV`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(index = 1))
        server.enqueue(deviceWanIpRead(wanIp = "192.168.255.249"))
        server.enqueue(taskAccepted()) // staging prep SPV
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted()) // prod SPV
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted()) // getParameterValues
        server.enqueue(emptyFaults())
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))

        val outcome = service.provision(
            sampleRequest().copy(
                ip = "192.168.30.213",
                ipSegment = "192.168.30.0/24",
                wanVlanId = 100,
            )
        )

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        server.takeRequest() // listDevices
        server.takeRequest() // tasks purge
        server.takeRequest() // faults purge
        server.takeRequest() // wan tree
        server.takeRequest() // wan ip read
        val prepBody = server.takeRequest().body.readUtf8()
        assertTrue(prepBody.contains("DHCP"), prepBody)
        assertTrue(!prepBody.contains("192.168.30.213"), prepBody)
        server.takeRequest() // faults after prep SPV
        val prodBody = server.takeRequest().body.readUtf8()
        assertTrue(prodBody.contains("Static"), prodBody)
        assertTrue(prodBody.contains("192.168.30.213"), prodBody)
    }

    @Test
    fun `unknown onu type waits for GenieACS before failing`() {
        server.enqueue(emptyDevices())
        val outcome = service.provision(
            sampleRequest().copy(onuTypeName = "HG8310", waitTimeoutMs = 1L),
        )
        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertTrue(
            outcome.message!!.contains("no contactó", ignoreCase = true) ||
                outcome.message!!.contains("sin perfil", ignoreCase = true),
        )
        assertTrue(server.requestCount > 0)
    }

    private fun sampleRequest() = Tr069ProvisionRequest(
        onuSerial = "VSOL0031C0B6",
        onuTypeName = "V2804AX15T",
        ip = "192.168.123.4",
        ipSegment = "192.168.123.0/24",
        wifiSsid24 = "acs2g",
        wifiPassword24 = "11111111",
        wifiSsid5 = "acs5g",
        wifiPassword5 = "11111111",
        wanVlanId = 1,
    )

    private fun emptyDevices() = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("[]")

    private fun deviceList() = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"B46415-V2804AX15T-12345B4641531C0B6",
              "_lastInform":"2026-08-20T12:00:00.000Z",
              "_lastBoot":"2026-08-20T11:55:00.000Z",
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

    private fun taskAccepted() = MockResponse()
        .setResponseCode(202)
        .addHeader("Content-Type", "application/json")
        .setBody("""{"_id":"task-1"}""")

    private fun emptyFaults() = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("[]")

    private fun emptyDeviceQueue() {
        server.enqueue(emptyTasks())
        server.enqueue(emptyFaults())
    }

    private fun emptyTasks() = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("[]")

    private fun taskFault(taskId: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "device":"B46415-V2804AX15T-12345B4641531C0B6",
              "channel":"task_$taskId",
              "code":"cwmp.9003",
              "message":"Invalid arguments",
              "detail":{
                "faultString":"Invalid arguments",
                "setParameterValuesFault":[
                  {"parameterName":"InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress","faultCode":"9001","faultString":"Request denied"}
                ]
              }
            }]
            """.trimIndent()
        )

    private fun deviceWanIpRead(index: Int = 1, wanIp: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"B46415-V2804AX15T-12345B4641531C0B6",
              "InternetGatewayDevice":{
                "WANDevice":{"1":{
                  "WANConnectionDevice":{
                    "$index":{"WANIPConnection":{"1":{"ExternalIPAddress":{"_value":"$wanIp"}}}}
                  }
                }}
              }
            }]
            """.trimIndent()
        )

    private fun wanConnectionTree(index: Int) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"B46415-V2804AX15T-12345B4641531C0B6",
              "InternetGatewayDevice":{
                "WANDevice":{"1":{
                  "WANConnectionDevice":{
                    "$index":{"WANIPConnection":{"1":{"ExternalIPAddress":{"_value":"192.168.123.4"}}}}
                  }
                }}
              }
            }]
            """.trimIndent()
        )

    private fun deviceWithSsids(ssid24: String, ssid5: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"B46415-V2804AX15T-12345B4641531C0B6",
              "InternetGatewayDevice":{
                "LANDevice":{"1":{
                  "WLANConfiguration":{
                    "5":{"SSID":{"_value":"$ssid24"}},
                    "1":{"SSID":{"_value":"$ssid5"}}
                  }
                }}
              }
            }]
            """.trimIndent()
        )
}
