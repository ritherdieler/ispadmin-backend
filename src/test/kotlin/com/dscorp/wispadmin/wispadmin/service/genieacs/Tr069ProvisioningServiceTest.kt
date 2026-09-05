package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.util.concurrent.atomic.AtomicLong

class Tr069ProvisioningServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: Tr069ProvisioningService
    private lateinit var profileRegistry: Tr069ModelProfileRegistry
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
        profileRegistry = mockk()
        every { profileRegistry.hasImportedProfiles() } returns true
        Tr069ModelProfiles.registerDynamicResolver { onuTypeName, productClass ->
            Tr069ModelProfiles.resolveBuiltin(onuTypeName, productClass)
        }
        service = Tr069ProvisioningService(client, properties, profileRegistry).withTimeControls(
            clock = { now.get() },
            sleeper = { ms -> now.addAndGet(ms) },
        )
    }

    @AfterEach
    fun tearDown() {
        Tr069ModelProfiles.registerDynamicResolver { _, _ -> null }
        server.shutdown()
    }

    @Test
    fun `no imported profiles returns configure profiles message`() {
        every { profileRegistry.hasImportedProfiles() } returns false
        server.enqueue(deviceList())
        emptyDeviceQueue()

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertEquals(Tr069ProvisioningService.MISSING_IMPORTED_PROFILES_MESSAGE, outcome.message)
        assertTrue(outcome.message!!.contains("Perfiles TR-069"))
    }

    @Test
    fun `dual wan creates WCD2 and WANIP then SPV client WAN`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueCreateClientWan()
        enqueueClientWanAndWifiSuccess("192.168.123.4")

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val requests = drainAllRequests()
        val addObjects = requests.filter { it.method == "POST" && it.body.contains("\"addObject\"") }
        assertEquals(2, addObjects.size, requests.toString())
        assertTrue(addObjects.all { !it.path.contains("connection_request") }, addObjects.map { it.path }.toString())
        assertTrue(addObjects.any { it.body.contains("WANConnectionDevice\"") }, addObjects.map { it.body }.toString())
        assertTrue(
            addObjects.any { it.body.contains("WANConnectionDevice.2.WANIPConnection") },
            addObjects.map { it.body }.toString(),
        )
        val spvPosts = requests.filter { it.method == "POST" && it.body.contains("setParameterValues") }
        assertEquals(1, spvPosts.size, requests.toString())
        assertTrue(spvPosts.first().path.contains("connection_request"), spvPosts.first().path)
        val wanAndWifiSpv = spvPosts.first().body
        assertTrue(wanAndWifiSpv.contains("WANConnectionDevice.2"), wanAndWifiSpv)
        assertTrue(wanAndWifiSpv.contains("INTERNET"), wanAndWifiSpv)
        assertTrue(wanAndWifiSpv.contains("192.168.123.4"), wanAndWifiSpv)
        assertTrue(wanAndWifiSpv.contains("WLANConfiguration"), wanAndWifiSpv)
        assertTrue(
            requests.none { it.method == "POST" && it.body.contains("\"refreshObject\"") },
            "cold start must skip refreshObject",
        )
    }

    @Test
    fun `queues AddObject then SPV without waiting for WANIP in ACS cache`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueCreateClientWan()
        enqueueClientWanAndWifiSuccess("192.168.123.4")

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val requests = drainAllRequests()
        val firstSpvIndex = requests.indexOfFirst { request ->
            request.method == "POST" && request.body.contains("setParameterValues")
        }
        assertTrue(firstSpvIndex > 0, "expected SPV after queued AddObject")
        val wanGetsBeforeSpv = requests.take(firstSpvIndex).count { request ->
            request.method == "GET" && request.path.contains("WANConnectionDevice")
        }
        assertTrue(wanGetsBeforeSpv <= 2, "S1 must not poll ACS until WANIP exists, got $wanGetsBeforeSpv")
    }

    @Test
    fun `stale ACS WCD2 after OLT reauth still AddObject when refresh shows only WCD1`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(1, 2))
        enqueueRefreshWanTree()
        server.enqueue(wanConnectionTree(1))
        server.enqueue(taskAccepted())
        server.enqueue(wanConnectionTree(1))
        server.enqueue(taskAccepted())
        enqueueClientWanAndWifiSuccess("192.168.123.4")

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val posts = drainPostBodies()
        assertTrue(posts.any { it.contains("\"refreshObject\"") }, posts.toString())
        assertTrue(posts.any { it.contains("\"addObject\"") && it.contains("WANConnectionDevice\"") }, posts.toString())
    }

    @Test
    fun `dual wan skips addObject when WCD2 already exists`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        enqueueClientWanAndWifiSuccess("192.168.123.4")

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val posts = drainPostBodies()
        assertTrue(posts.none { it.contains("\"addObject\"") }, posts.toString())
        assertTrue(posts.any { it.contains("WANConnectionDevice.2") && it.contains("ExternalIPAddress") })
    }

    @Test
    fun `does not touch WCD1 at all`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueCreateClientWan()
        enqueueClientWanAndWifiSuccess("192.168.123.4")

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val posts = drainPostBodies()
        assertTrue(posts.none { it.contains(".WANConnectionDevice.1.") }, posts.toString())
    }

    @Test
    fun `does not send unlock DHCP SPV on dual wan flow`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        enqueueClientWanAndWifiSuccess("192.168.30.216")

        val outcome = service.provision(
            sampleRequest().copy(ip = "192.168.30.216", ipSegment = "192.168.30.0/24", wanVlanId = 100),
        )

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val posts = drainPostBodies()
        assertTrue(posts.none { it.contains("\"DHCP\"") }, posts.toString())
    }

    @Test
    fun `verifies client IP on WCD2 not WCD1`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        enqueueClientWanAndWifiSuccess("192.168.30.216")

        val outcome = service.provision(
            sampleRequest().copy(ip = "192.168.30.216", ipSegment = "192.168.30.0/24", wanVlanId = 100),
        )

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val getPaths = drainGetPaths()
        assertTrue(getPaths.any { it.contains("WANConnectionDevice.2") && it.contains("ExternalIPAddress") }, getPaths.toString())
        assertTrue(getPaths.any { it.contains("WANConnectionDevice.2") && it.contains("ConnectionStatus") }, getPaths.toString())
        assertTrue(getPaths.none { it.contains("WANConnectionDevice.1") && it.contains("ExternalIPAddress") }, getPaths.toString())
    }

    @Test
    fun `does not complete while client WAN ConnectionStatus is Disconnected`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        repeat(6) {
            server.enqueue(emptyFaults())
            server.enqueue(deviceClientWanIp("192.168.30.216", connectionStatus = "Disconnected"))
            server.enqueue(deviceClientWanIp("192.168.30.216", connectionStatus = "Disconnected"))
        }

        val outcome = service.provision(
            sampleRequest().copy(
                ip = "192.168.30.216",
                ipSegment = "192.168.30.0/24",
                wanVlanId = 100,
                wifiSsid24 = null,
                wifiPassword24 = null,
                wifiSsid5 = null,
                wifiPassword5 = null,
            ),
        )

        assertEquals(Tr069ProvisionStatus.PENDING, outcome.status)
        assertTrue(
            outcome.message!!.contains("ConnectionStatus") ||
                outcome.message!!.contains("IP/SSID"),
            outcome.message,
        )
    }

    @Test
    fun `F6600R uses sibling WANIPConnection 2 on GPON WCD1`() {
        val f6600r = Tr069ModelProfile(
            productClass = "F6600R",
            wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
            wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
            clientWanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2",
            clientVlanParameters = listOf(
                Tr069VlanParameterSpec(
                    path = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2.X_ZTE-COM_VLANID",
                ),
                Tr069VlanParameterSpec(
                    path = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2.X_ZTE-COM_VLANEnable",
                    valueKind = Tr069VlanValueKind.ENABLE_TRUE,
                ),
            ),
        )
        Tr069ModelProfiles.registerDynamicResolver { _, productClass ->
            if (productClass == "F6600R") f6600r else Tr069ModelProfiles.resolveBuiltin(null, productClass)
        }
        server.enqueue(f6600rDeviceList())
        emptyDeviceQueue()
        enqueueF6600rCreateClientWan()
        enqueueF6600rClientWanAndWifiSuccess("192.168.123.4")

        val outcome = service.provision(
            sampleRequest().copy(
                onuSerial = "ZTEGDC47C838",
                onuTypeName = "F6600R",
                wifiSsid24 = "lab-zte-e2e-24",
                wifiSsid5 = "lab-zte-e2e-5",
            ),
        )

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val posts = drainPostBodies()
        assertTrue(
            posts.none {
                it.contains("\"addObject\"") &&
                    it.contains("WANConnectionDevice\"") &&
                    !it.contains("WANIPConnection")
            },
            posts.toString(),
        )
        assertTrue(
            posts.any { it.contains("\"addObject\"") && it.contains("WANDevice.1.WANConnectionDevice.1.WANIPConnection") },
            posts.toString(),
        )
        val wanSpv = posts.first { it.contains("setParameterValues") && it.contains("ExternalIPAddress") }
        assertTrue(wanSpv.contains("WANDevice.1.WANConnectionDevice.1.WANIPConnection.2"), wanSpv)
        assertTrue(!wanSpv.contains("WANDevice.2"), wanSpv)
        assertTrue(!wanSpv.contains("WANConnectionDevice.2"), wanSpv)
        assertTrue(!wanSpv.contains("WANIPConnection.1."), wanSpv)
        assertTrue(!wanSpv.contains("X_CT-COM_ServiceList"), wanSpv)
        assertTrue(wanSpv.contains("192.168.123.4"), wanSpv)
    }

    @Test
    fun `Huawei HG8145 queues WAN SPV then WiFi SPV with one CR and X_HW leaves`() {
        registerHuaweiProfile()
        server.enqueue(huaweiDeviceList())
        emptyDeviceQueue()
        enqueueCreateClientWan()
        enqueueHuaweiApplySuccess("192.168.30.250")

        val outcome = service.provision(
            sampleRequest().copy(
                onuSerial = "48575443C6FBA6AA",
                onuTypeName = "HG8145X6",
                ip = "192.168.30.250",
                ipSegment = "192.168.30.0/24",
                wanVlanId = 100,
                wifiSsid24 = "lab-hg8145-24",
                wifiSsid5 = "lab-hg8145-5",
            ),
        )

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val requests = drainAllRequests()
        val addObjects = requests.filter { it.method == "POST" && it.body.contains("\"addObject\"") }
        assertTrue(addObjects.all { !it.path.contains("connection_request") }, addObjects.map { it.path }.toString())
        val spvs = requests.filter { it.method == "POST" && it.body.contains("setParameterValues") }
        assertEquals(2, spvs.size, spvs.map { it.body.take(80) }.toString())
        val wanSpv = spvs.first { it.body.contains("ExternalIPAddress") }
        val wifiSpv = spvs.first { it.body.contains("WLANConfiguration") }
        assertTrue(!wanSpv.path.contains("connection_request"), wanSpv.path)
        assertTrue(wifiSpv.path.contains("connection_request"), wifiSpv.path)
        assertTrue(wanSpv.body.contains("X_HW_SERVICELIST"), wanSpv.body)
        assertTrue(wanSpv.body.contains("X_HW_LANBIND"), wanSpv.body)
        assertTrue(wanSpv.body.contains("X_HW_VLAN"), wanSpv.body)
        assertTrue(!wanSpv.body.contains("X_ZTE-COM_"), wanSpv.body)
        assertTrue(!wanSpv.body.contains("WLANConfiguration"), wanSpv.body)
        assertTrue(!wifiSpv.body.contains("BeaconType"), wifiSpv.body)
        assertTrue(!wifiSpv.body.contains("ExternalIPAddress"), wifiSpv.body)
    }

    @Test
    fun `Huawei isolated L3 SPV when GPV leaves NAT off and empty mask`() {
        registerHuaweiProfile()
        server.enqueue(huaweiDeviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        server.enqueue(taskAccepted())
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        server.enqueue(deviceHuaweiL3(nat = false, mask = "", dns = "192.168.0.1"))
        server.enqueue(deviceHuaweiL3(nat = false, mask = "", dns = "192.168.0.1"))
        server.enqueue(deviceHuaweiL3(nat = false, mask = "", dns = "192.168.0.1"))
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(emptyFaults())
        server.enqueue(deviceClientWanIp("192.168.30.250"))
        server.enqueue(deviceClientWanIp("192.168.30.250"))
        server.enqueue(deviceWithSsids("lab-hg8145-24", "lab-hg8145-5", ssid24Index = 1, ssid5Index = 5))
        server.enqueue(deviceWithSsids("lab-hg8145-24", "lab-hg8145-5", ssid24Index = 1, ssid5Index = 5))

        val outcome = service.provision(
            sampleRequest().copy(
                onuSerial = "48575443C6FBA6AA",
                onuTypeName = "HG8145X6",
                ip = "192.168.30.250",
                ipSegment = "192.168.30.0/24",
                wanVlanId = 100,
                wifiSsid24 = "lab-hg8145-24",
                wifiSsid5 = "lab-hg8145-5",
            ),
        )

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val posts = drainPostBodies()
        val isolated = posts.last { it.contains("setParameterValues") }
        assertTrue(isolated.contains("NATEnabled"), isolated)
        assertTrue(isolated.contains("SubnetMask"), isolated)
        assertTrue(isolated.contains("DNSServers"), isolated)
        assertTrue(!isolated.contains("WLANConfiguration"), isolated)
    }

    @Test
    fun `addObject fault returns MANUAL_REQUIRED`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(1))
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"detail":"AddObject rejected","error":400}""")
        )

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertTrue(outcome.message!!.contains("400"), outcome.message)
    }

    @Test
    fun `client WAN SPV fault returns MANUAL_REQUIRED`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        server.enqueue(taskAccepted())
        server.enqueue(wifiKeyPassphraseFault(taskId = "task-1"))

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertTrue(outcome.message!!.contains("KeyPassphrase", ignoreCase = true))
    }
    @Test
    fun `device appears on second poll then COMPLETE`() {
        server.enqueue(emptyDevices())
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        enqueueClientWanAndWifiSuccess("192.168.123.4")

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
        enqueueClientWanAlreadyPresent()
        enqueueClientWanAndWifiSuccess("192.168.123.4")

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
        enqueueClientWanAlreadyPresent()
        enqueueClientWanAndWifiSuccess("192.168.123.4")

        val outcome = service.provision(sampleRequest().copy(wanVlanId = 100))

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val wanSpv = drainPostBodies().first { it.contains("setParameterValues") && it.contains("ExternalIPAddress") }
        assertTrue(wanSpv.contains("\"100\""), "expected VLAN 100 in SPV payload: $wanSpv")
        assertTrue(wanSpv.contains("X_CT-COM_VLANIDMark"), wanSpv)
        assertTrue(wanSpv.contains("2_INTERNET_R_VID_100"), wanSpv)
    }

    @Test
    fun `connectionName override is used in WAN SPV`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        enqueueClientWanAndWifiSuccess("192.168.123.4")

        val outcome = service.provision(
            sampleRequest().copy(connectionName = "744 INTERNET JUAN PEREZ"),
        )

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val wanSpv = drainPostBodies().first { it.contains("setParameterValues") && it.contains("ExternalIPAddress") }
        assertTrue(wanSpv.contains("744 INTERNET JUAN PEREZ"), wanSpv)
        assertFalse(wanSpv.contains("2_INTERNET_R_VID_1"), wanSpv)
    }

    @Test
    fun `identityOnly sets WAN Name without IP or WiFi`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        server.enqueue(wanConnectionTree(1))
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())

        val outcome = service.provision(
            sampleRequest().copy(
                ip = null,
                ipSegment = null,
                wifiSsid24 = null,
                wifiSsid5 = null,
                identityOnly = true,
                connectionName = "12 TV CARLOS LOPEZ",
            ),
        )

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val bodies = drainPostBodies()
        val nameSpv = bodies.first { it.contains("setParameterValues") }
        assertTrue(nameSpv.contains("12 TV CARLOS LOPEZ"), nameSpv)
        assertFalse(nameSpv.contains("ExternalIPAddress"), nameSpv)
        assertFalse(nameSpv.contains("SSID"), nameSpv)
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
        enqueueClientWanAlreadyPresent()
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
        enqueueClientWanAlreadyPresent()
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
        enqueueClientWanAlreadyPresent()
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        server.enqueue(taskFault(taskId = "task-1"))

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, outcome.status)
        assertEquals(outcome.message, outcome.error)
        assertTrue(outcome.message!!.contains("Request denied"))
        assertTrue(outcome.message!!.contains("ExternalIPAddress"))
    }

    @Test
    fun `SSID verification timeout returns PENDING not MANUAL_REQUIRED`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        repeat(6) {
            server.enqueue(emptyFaults())
            server.enqueue(deviceClientWanIp("192.168.123.4"))
            server.enqueue(deviceClientWanIp("192.168.123.4"))
            server.enqueue(deviceWithSsids("wrong24", "wrong5"))
            server.enqueue(deviceWithSsids("wrong24", "wrong5"))
        }

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.PENDING, outcome.status)
        assertEquals(outcome.message, outcome.error)
        assertTrue(outcome.message!!.contains("SSID", ignoreCase = true))
        assertTrue(outcome.message!!.contains("tiempo de espera", ignoreCase = true))
    }

    @Test
    fun `apply deadline is independent of serial discovery time`() {
        server.enqueue(emptyDevices())
        server.enqueue(emptyDevices())
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(deviceClientWanIp("192.168.123.4"))
        server.enqueue(deviceClientWanIp("192.168.123.4"))
        server.enqueue(deviceWithSsids("wrong24", "wrong5"))
        server.enqueue(deviceWithSsids("wrong24", "wrong5"))
        server.enqueue(emptyFaults())
        server.enqueue(deviceClientWanIp("192.168.123.4"))
        server.enqueue(deviceClientWanIp("192.168.123.4"))
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))

        val outcome = service.provision(sampleRequest().copy(waitTimeoutMs = 10_000))

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
    }

    @Test
    fun `SPV 9005 recreates WANIP and retries SPV`() {
        server.enqueue(deviceList())
        emptyDeviceQueue()
        enqueueClientWanAlreadyPresent()
        server.enqueue(taskAccepted())
        server.enqueue(invalidParameterNameFault(taskId = "task-1"))
        server.enqueue(taskAccepted())
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(deviceClientWanIp("192.168.123.4"))
        server.enqueue(deviceClientWanIp("192.168.123.4"))
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))

        val outcome = service.provision(sampleRequest())

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcome.status)
        val posts = drainPostBodies()
        assertEquals(2, posts.count { it.contains("setParameterValues") }, posts.toString())
        assertTrue(
            posts.any { it.contains("\"addObject\"") && it.contains("WANIPConnection") },
            posts.toString(),
        )
    }

    private fun enqueueRefreshWanTree() {
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
    }

    private fun enqueueClientWanAlreadyPresent() {
        server.enqueue(wanConnectionTree(1, 2))
        enqueueRefreshWanTree()
        server.enqueue(wanConnectionTree(1, 2))
        server.enqueue(wanConnectionTree(1, 2))
    }

    private fun enqueueCreateClientWan() {
        server.enqueue(wanConnectionTree(1))
        server.enqueue(taskAccepted())
        server.enqueue(wanConnectionTree(1))
        server.enqueue(taskAccepted())
    }

    private fun enqueueF6600rCreateClientWan() {
        server.enqueue(wanConnectionTree(1, wanDeviceIndex = 1, withWanIp = true))
        enqueueRefreshWanTree()
        server.enqueue(wanConnectionTree(1, wanDeviceIndex = 1, withWanIp = true))
        server.enqueue(wanConnectionTree(1, wanDeviceIndex = 1, withWanIp = true))
        server.enqueue(taskAccepted())
    }

    private fun enqueueF6600rClientWanAndWifiSuccess(ip: String) {
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(deviceClientWanIp(ip, wanDeviceIndex = 1, wcdIndex = 1, wanIpInstance = 2))
        server.enqueue(deviceClientWanIp(ip, wanDeviceIndex = 1, wcdIndex = 1, wanIpInstance = 2))
        server.enqueue(deviceWithSsids("lab-zte-e2e-24", "lab-zte-e2e-5", ssid24Index = 1, ssid5Index = 5))
        server.enqueue(deviceWithSsids("lab-zte-e2e-24", "lab-zte-e2e-5", ssid24Index = 1, ssid5Index = 5))
    }

    private fun enqueueClientWanAndWifiSuccess(ip: String) {
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(deviceClientWanIp(ip))
        server.enqueue(deviceClientWanIp(ip))
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))
        server.enqueue(deviceWithSsids("acs2g", "acs5g"))
    }

    private fun registerHuaweiProfile() {
        val huawei = Tr069ModelProfile(
            productClass = "HG8145X6",
            wanIpConnectionPath = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1",
            wlan24Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
            wlan5Path = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
            vlanParameters = listOf(
                Tr069VlanParameterSpec(
                    path = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_HW_VLAN",
                ),
            ),
            wifiSecurityPrep = Tr069WifiSecurityDefaults.STANDARD_OPEN_WIFI_PREP,
        )
        Tr069ModelProfiles.registerDynamicResolver { onuTypeName, productClass ->
            if (onuTypeName == "HG8145X6" || productClass == "HG8145X6") {
                huawei
            } else {
                Tr069ModelProfiles.resolveBuiltin(onuTypeName, productClass)
            }
        }
    }

    private fun enqueueHuaweiApplySuccess(ip: String) {
        server.enqueue(taskAccepted())
        server.enqueue(taskAccepted())
        server.enqueue(emptyFaults())
        server.enqueue(emptyFaults())
        server.enqueue(taskAccepted())
        repeat(3) { server.enqueue(emptyDevices()) }
        server.enqueue(emptyFaults())
        server.enqueue(deviceClientWanIp(ip))
        server.enqueue(deviceClientWanIp(ip))
        server.enqueue(deviceWithSsids("lab-hg8145-24", "lab-hg8145-5", ssid24Index = 1, ssid5Index = 5))
        server.enqueue(deviceWithSsids("lab-hg8145-24", "lab-hg8145-5", ssid24Index = 1, ssid5Index = 5))
    }

    private data class RecordedCall(
        val method: String?,
        val path: String,
        val body: String,
    )

    private fun drainAllRequests(): List<RecordedCall> {
        val requests = mutableListOf<RecordedCall>()
        while (true) {
            val recorded = server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            requests += RecordedCall(
                method = recorded.method,
                path = recorded.path.orEmpty(),
                body = recorded.body.readUtf8(),
            )
        }
        return requests
    }

    private fun drainPostBodies(): List<String> {
        val bodies = mutableListOf<String>()
        while (true) {
            val recorded = server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            if (recorded.method == "POST") {
                bodies += recorded.body.readUtf8()
            }
        }
        return bodies
    }

    private fun drainGetPaths(): List<String> {
        val paths = mutableListOf<String>()
        while (true) {
            val recorded = server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            if (recorded.method == "GET") {
                paths += recorded.path.orEmpty()
            }
        }
        return paths
    }

    private fun invalidParameterNameFault(taskId: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "device":"B46415-V2804AX15T-12345B4641531C0B6",
              "channel":"task_$taskId",
              "code":"cwmp.9005",
              "message":"Invalid parameter name",
              "detail":{
                "faultString":"Invalid parameter name",
                "setParameterValuesFault":[
                  {"parameterName":"InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.ExternalIPAddress","faultCode":"9005","faultString":"Invalid parameter name"}
                ]
              }
            }]
            """.trimIndent()
        )

    private fun wifiKeyPassphraseFault(taskId: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "device":"B46415-V2804AX15T-12345B4641531C0B6",
              "channel":"task_$taskId",
              "code":"cwmp.9007",
              "message":"Invalid parameter value",
              "detail":{
                "faultString":"Invalid parameter value",
                "setParameterValuesFault":[
                  {"parameterName":"InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.KeyPassphrase","faultCode":"9007","faultString":"Invalid parameter value"}
                ]
              }
            }]
            """.trimIndent()
        )

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

    private fun huaweiDeviceList() = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"00259E-HG8145X6-48575443C6FBA6AA",
              "_lastInform":"2026-08-25T12:00:00.000Z",
              "_lastBoot":"2026-08-25T11:55:00.000Z",
              "_deviceId":{
                "_SerialNumber":"48575443C6FBA6AA",
                "_ProductClass":"HG8145X6",
                "_Manufacturer":"Huawei Technologies Co., Ltd",
                "_OUI":"00259E",
                "_SoftwareVersion":"V5R021C10S165",
                "_HardwareVersion":"V5R021"
              },
              "InternetGatewayDevice":{
                "ManagementServer":{
                  "ConnectionRequestURL":{"_value":"http://192.168.255.245:7547/tr069"}
                }
              }
            }]
            """.trimIndent()
        )

    private fun f6600rDeviceList() = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"5872C9-F6600R-ZTEGDC47C838",
              "_lastInform":"2026-08-25T12:00:00.000Z",
              "_lastBoot":"2026-08-25T11:55:00.000Z",
              "_deviceId":{
                "_SerialNumber":"ZTEGDC47C838",
                "_ProductClass":"F6600R",
                "_Manufacturer":"ZTE",
                "_OUI":"5872C9",
                "_SoftwareVersion":"V9.0.10P2N38",
                "_HardwareVersion":"V9.0.21"
              },
              "InternetGatewayDevice":{
                "ManagementServer":{
                  "ConnectionRequestURL":{"_value":"http://192.168.255.242:7547/tr069"}
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

    private fun deviceClientWanIp(
        wanIp: String,
        wanDeviceIndex: Int = 1,
        wcdIndex: Int = 2,
        wanIpInstance: Int = 1,
        connectionStatus: String = "Connected",
    ) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"B46415-V2804AX15T-12345B4641531C0B6",
              "InternetGatewayDevice":{
                "WANDevice":{"$wanDeviceIndex":{
                  "WANConnectionDevice":{"$wcdIndex":{
                    "WANIPConnection":{"$wanIpInstance":{
                      "ExternalIPAddress":{"_value":"$wanIp"},
                      "ConnectionStatus":{"_value":"$connectionStatus"}
                    }}
                  }}
                }}
              }
            }]
            """.trimIndent()
        )

    private fun wanConnectionTree(vararg indices: Int) = wanConnectionTree(
        indices = indices.toList(),
        withWanIp = true,
        wanDeviceIndex = 1,
    )

    private fun wanConnectionTree(
        vararg indices: Int,
        wanDeviceIndex: Int,
        withWanIp: Boolean,
    ) = wanConnectionTree(indices.toList(), withWanIp = withWanIp, wanDeviceIndex = wanDeviceIndex)

    private fun wanConnectionTreeWithoutWanIp(vararg indices: Int) =
        wanConnectionTree(indices.toList(), withWanIp = false, wanDeviceIndex = 1)

    private fun wanConnectionTreeWithWanIpInstance(
        wcdIndex: Int,
        wanIpInstance: Int,
        wanDeviceIndex: Int = 1,
    ) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"5872C9-F6600R-ZTEGDC47C838",
              "InternetGatewayDevice":{
                "WANDevice":{"$wanDeviceIndex":{
                  "WANConnectionDevice":{"$wcdIndex":{
                    "WANIPConnection":{"$wanIpInstance":{
                      "ExternalIPAddress":{"_value":"192.168.123.4"},
                      "ConnectionStatus":{"_value":"Connected"}
                    }}
                  }}
                }}
              }
            }]
            """.trimIndent()
        )

    private fun wanConnectionTree(
        indices: List<Int>,
        withWanIp: Boolean,
        wanDeviceIndex: Int = 1,
    ): MockResponse {
        val slots = indices.joinToString(",") { index ->
            val body = if (withWanIp || index != indices.last()) {
                """"$index":{"WANIPConnection":{"1":{"ExternalIPAddress":{"_value":"192.168.123.4"},"ConnectionStatus":{"_value":"Connected"}}}}"""
            } else {
                """"$index":{}"""
            }
            body
        }
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                [{
                  "_id":"B46415-V2804AX15T-12345B4641531C0B6",
                  "InternetGatewayDevice":{
                    "WANDevice":{"$wanDeviceIndex":{
                      "WANConnectionDevice":{$slots}
                    }}
                  }
                }]
                """.trimIndent()
            )
    }

    private fun deviceHuaweiL3(
        nat: Boolean,
        mask: String,
        dns: String,
        wanDeviceIndex: Int = 1,
        wcdIndex: Int = 2,
        wanIpInstance: Int = 1,
    ) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"00259E-HG8145X6-48575443C6FBA6AA",
              "InternetGatewayDevice":{
                "WANDevice":{"$wanDeviceIndex":{
                  "WANConnectionDevice":{"$wcdIndex":{
                    "WANIPConnection":{"$wanIpInstance":{
                      "NATEnabled":{"_value":"$nat"},
                      "SubnetMask":{"_value":"$mask"},
                      "DNSServers":{"_value":"$dns"}
                    }}
                  }}
                }}
              }
            }]
            """.trimIndent()
        )

    private fun deviceWithSsids(
        ssid24: String,
        ssid5: String,
        ssid24Index: Int = 5,
        ssid5Index: Int = 1,
    ) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(
            """
            [{
              "_id":"B46415-V2804AX15T-12345B4641531C0B6",
              "InternetGatewayDevice":{
                "LANDevice":{"1":{
                  "WLANConfiguration":{
                    "$ssid24Index":{"SSID":{"_value":"$ssid24"}},
                    "$ssid5Index":{"SSID":{"_value":"$ssid5"}}
                  }
                }}
              }
            }]
            """.trimIndent()
        )
}
