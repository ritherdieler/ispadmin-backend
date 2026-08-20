package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class SubscriptionAcsOpsServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val acsRepository = mockk<SubscriptionAcsRepository>()
    private val client = mockk<GenieAcsClient>()
    private val syncService = mockk<SubscriptionAcsSyncService>(relaxed = true)
    private lateinit var service: SubscriptionAcsOpsService

    @BeforeEach
    fun setUp() {
        service = SubscriptionAcsOpsService(
            subscriptionRepository = subscriptionRepository,
            acsRepository = acsRepository,
            client = client,
            syncService = syncService,
        )
    }

    @Test
    fun `getAcs returns dto when row exists`() {
        every { acsRepository.findById(10) } returns Optional.of(
            SubscriptionAcs(
                subscriptionId = 10,
                genieacsDeviceId = "B46415-V2804AX15T-12345B4641531C0B6",
                provisionStatus = Tr069ProvisionStatus.COMPLETE,
            )
        )

        val dto = service.getAcs(10)

        assertEquals(10, dto.subscriptionId)
        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", dto.genieacsDeviceId)
        assertEquals(Tr069ProvisionStatus.COMPLETE, dto.provisionStatus)
    }

    @Test
    fun `getAcs throws when row missing`() {
        every { acsRepository.findById(10) } returns Optional.empty()

        assertThrows(NoSuchElementException::class.java) {
            service.getAcs(10)
        }
    }

    @Test
    fun `refresh upserts snapshot from GenieACS device by stored deviceId`() {
        val subscription = Subscription(
            firstName = "A",
            lastName = "B",
            dni = "1",
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply {
            id = 10
            ip = "192.168.123.4"
            wifiSsid24 = "acs2g"
            wifiSsid5 = "acs5g"
            tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE
            tr069DeviceId = "B46415-V2804AX15T-12345B4641531C0B6"
            fiberOnu = Onu(sn = "VSOL0031C0B6")
        }
        every { subscriptionRepository.findById(10) } returns Optional.of(subscription)
        every { acsRepository.findById(10) } returns Optional.of(
            SubscriptionAcs(
                subscriptionId = 10,
                genieacsDeviceId = "B46415-V2804AX15T-12345B4641531C0B6",
                provisionStatus = Tr069ProvisionStatus.COMPLETE,
                ssid24 = "acs2g",
                ssid5 = "acs5g",
            )
        ) andThen Optional.of(
            SubscriptionAcs(
                subscriptionId = 10,
                genieacsDeviceId = "B46415-V2804AX15T-12345B4641531C0B6",
                provisionStatus = Tr069ProvisionStatus.COMPLETE,
                lastInformAt = java.time.LocalDateTime.of(2026, 8, 20, 13, 0),
                productClass = "V2804AX15T",
                ssid24 = "acs2g",
                ssid5 = "acs5g",
            )
        )
        every { client.listDevices() } returns listOf(
            GenieAcsDevice(
                id = "B46415-V2804AX15T-12345B4641531C0B6",
                serialNumber = "12345B4641531C0B6",
                productClass = "V2804AX15T",
                lastInform = "2026-08-20T13:00:00.000Z",
                oui = "B46415",
                manufacturer = "VSOL",
                softwareVersion = "V2.0",
                connectionRequestUrl = "http://192.168.123.4:7547/tr069",
            )
        )
        val outcomeSlot = slot<Tr069ProvisionOutcome>()
        every {
            syncService.upsertFromProvision(eq(10), capture(outcomeSlot), any())
        } returns Unit

        val dto = service.refresh(10)

        assertEquals(Tr069ProvisionStatus.COMPLETE, outcomeSlot.captured.status)
        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", outcomeSlot.captured.deviceId)
        assertEquals("V2804AX15T", outcomeSlot.captured.acsSnapshot?.productClass)
        assertEquals("acs2g", outcomeSlot.captured.acsSnapshot?.ssid24)
        assertEquals("V2.0", outcomeSlot.captured.acsSnapshot?.softwareVersion)
        assertEquals(10, dto.subscriptionId)
        assertEquals("V2804AX15T", dto.productClass)
    }

    @Test
    fun `refresh throws when device not found in GenieACS`() {
        val subscription = Subscription(
            firstName = "A",
            lastName = "B",
            dni = "1",
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply {
            id = 10
            tr069DeviceId = "missing-device"
            fiberOnu = Onu(sn = "VSOL0031C0B6")
        }
        every { subscriptionRepository.findById(10) } returns Optional.of(subscription)
        every { acsRepository.findById(10) } returns Optional.of(
            SubscriptionAcs(subscriptionId = 10, genieacsDeviceId = "missing-device")
        )
        every { client.listDevices() } returns emptyList()

        val ex = assertThrows(IllegalStateException::class.java) {
            service.refresh(10)
        }
        assertTrue(ex.message!!.contains("GenieACS"))
        verify(exactly = 0) { syncService.upsertFromProvision(any(), any(), any()) }
    }

    @Test
    fun `reboot sends task with connection_request and updates last task`() {
        every { subscriptionRepository.findById(10) } returns Optional.of(
            Subscription(
                firstName = "A",
                lastName = "B",
                dni = "1",
                equipmentCondition = EquipmentCondition.LOAN,
            ).apply {
                id = 10
                tr069DeviceId = "B46415-V2804AX15T-12345B4641531C0B6"
            }
        )
        every { acsRepository.findById(10) } returns Optional.of(
            SubscriptionAcs(
                subscriptionId = 10,
                genieacsDeviceId = "B46415-V2804AX15T-12345B4641531C0B6",
            )
        )
        every {
            client.reboot("B46415-V2804AX15T-12345B4641531C0B6", connectionRequest = true)
        } returns GenieAcsTaskResult(
            statusCode = 202,
            body = """{"_id":"task-reboot-1"}""",
            accepted = true,
            taskId = "task-reboot-1",
        )
        val saved = slot<SubscriptionAcs>()
        every { acsRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.reboot(10)

        assertTrue(result.accepted)
        assertEquals("task-reboot-1", result.taskId)
        assertEquals("task-reboot-1", saved.captured.lastTaskId)
        assertEquals("accepted", saved.captured.lastTaskStatus)
        assertNotNull(saved.captured.lastTaskAt)
    }

    @Test
    fun `reboot falls back without connection_request when CR credentials fail`() {
        every { subscriptionRepository.findById(10) } returns Optional.of(
            Subscription(
                firstName = "A",
                lastName = "B",
                dni = "1",
                equipmentCondition = EquipmentCondition.LOAN,
            ).apply { id = 10 }
        )
        every { acsRepository.findById(10) } returns Optional.of(
            SubscriptionAcs(
                subscriptionId = 10,
                genieacsDeviceId = "device-1",
            )
        )
        every { client.reboot("device-1", connectionRequest = true) } returns GenieAcsTaskResult(
            statusCode = 202,
            body = GenieAcsClient.CR_CREDENTIALS_ERROR,
            accepted = true,
            connectionRequestFailed = true,
            taskId = "t1",
        )
        every { client.reboot("device-1", connectionRequest = false) } returns GenieAcsTaskResult(
            statusCode = 202,
            body = """{"_id":"t2"}""",
            accepted = true,
            taskId = "t2",
        )
        every { acsRepository.save(any()) } answers { firstArg() }

        val result = service.reboot(10)

        assertEquals("t2", result.taskId)
        verify { client.reboot("device-1", connectionRequest = false) }
    }
}
