package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

class SubscriptionAcsSyncServiceTest {

    private val repository = mockk<SubscriptionAcsRepository>()
    private val client = mockk<GenieAcsClient>()
    private lateinit var service: SubscriptionAcsSyncService
    private val saved = slot<SubscriptionAcs>()

    @BeforeEach
    fun setUp() {
        service = SubscriptionAcsSyncService(repository, client)
        every { repository.findById(any()) } returns Optional.empty()
        every { repository.save(capture(saved)) } answers { firstArg() }
        every { client.listTags(any()) } returns emptyList()
    }

    @Test
    fun `COMPLETE upserts snapshot with device metadata and ssids`() {
        service.upsertFromProvision(
            subscriptionId = 42,
            outcome = Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.COMPLETE,
                deviceId = "B46415-V2804AX15T-12345B4641531C0B6",
                message = "ONU configurada automáticamente por TR-069.",
                acsSnapshot = Tr069AcsSnapshot(
                    serialSuffix = "31C0B6",
                    lastInformAt = LocalDateTime.of(2026, 8, 20, 12, 0),
                    productClass = "V2804AX15T",
                    oui = "B46415",
                    manufacturer = "VSOL",
                    connectionRequestUrl = "http://192.168.123.4:7547/tr069",
                    softwareVersion = "V1.0",
                    hardwareVersion = "V1.1",
                    lastBootAt = LocalDateTime.of(2026, 8, 20, 11, 55),
                    wanIpCache = "192.168.123.4",
                    ssid24 = "acs2g",
                    ssid5 = "acs5g",
                    lastTaskId = "task-1",
                    lastTaskStatus = "accepted",
                    lastTaskAt = LocalDateTime.of(2026, 8, 20, 12, 1),
                ),
            ),
            smartoltSerial = "VSOL0031C0B6",
        )

        verify(exactly = 1) { repository.save(any()) }
        val row = saved.captured
        assertEquals(42, row.subscriptionId)
        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", row.genieacsDeviceId)
        assertEquals("31C0B6", row.serialSuffix)
        assertEquals("VSOL0031C0B6", row.smartoltSerial)
        assertEquals(Tr069ProvisionStatus.COMPLETE, row.provisionStatus)
        assertNull(row.lastError)
        assertEquals("V2804AX15T", row.productClass)
        assertEquals("B46415", row.oui)
        assertEquals("VSOL", row.manufacturer)
        assertEquals("http://192.168.123.4:7547/tr069", row.connectionRequestUrl)
        assertEquals("192.168.123.4", row.wanIpCache)
        assertEquals("acs2g", row.ssid24)
        assertEquals("acs5g", row.ssid5)
        assertEquals("V1.0", row.softwareVersion)
        assertEquals("V1.1", row.hardwareVersion)
        assertEquals("task-1", row.lastTaskId)
        assertEquals("accepted", row.lastTaskStatus)
        assertTrue(row.provisionedAt != null)
        assertTrue(row.updatedAt != null)
    }

    @Test
    fun `MANUAL_REQUIRED with deviceId still upserts partial snapshot`() {
        service.upsertFromProvision(
            subscriptionId = 7,
            outcome = Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                deviceId = "B46415-V2804AX15T-12345B4641531C0B6",
                error = "SSID no verificado",
                message = "Configure manualmente",
                acsSnapshot = Tr069AcsSnapshot(
                    serialSuffix = "31C0B6",
                    productClass = "V2804AX15T",
                    lastTaskId = "task-9",
                    lastTaskStatus = "accepted",
                ),
            ),
            smartoltSerial = "VSOL0031C0B6",
        )

        verify(exactly = 1) { repository.save(any()) }
        assertEquals(Tr069ProvisionStatus.MANUAL_REQUIRED, saved.captured.provisionStatus)
        assertEquals("SSID no verificado", saved.captured.lastError)
        assertEquals("B46415-V2804AX15T-12345B4641531C0B6", saved.captured.genieacsDeviceId)
    }

    @Test
    fun `MANUAL_REQUIRED without deviceId skips upsert`() {
        service.upsertFromProvision(
            subscriptionId = 7,
            outcome = Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.MANUAL_REQUIRED,
                deviceId = null,
                error = "La ONU no contactó al ACS",
            ),
            smartoltSerial = "VSOL0031C0B6",
        )

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `NA skips upsert`() {
        service.upsertFromProvision(
            subscriptionId = 1,
            outcome = Tr069ProvisionOutcome(status = Tr069ProvisionStatus.NA),
            smartoltSerial = null,
        )
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `upsert updates existing row without wiping prior soft fields when snapshot partial`() {
        val existing = SubscriptionAcs(
            subscriptionId = 42,
            genieacsDeviceId = "old-id",
            softwareVersion = "keep-me",
            hardwareVersion = "hw-keep",
            connectionRequestUrl = "http://old",
            updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        )
        every { repository.findById(42) } returns Optional.of(existing)

        service.upsertFromProvision(
            subscriptionId = 42,
            outcome = Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.COMPLETE,
                deviceId = "new-device",
                acsSnapshot = Tr069AcsSnapshot(
                    serialSuffix = "31C0B6",
                    ssid24 = "new-ssid",
                ),
            ),
            smartoltSerial = "VSOL0031C0B6",
        )

        assertEquals("new-device", saved.captured.genieacsDeviceId)
        assertEquals("new-ssid", saved.captured.ssid24)
        assertEquals("keep-me", saved.captured.softwareVersion)
        assertEquals("hw-keep", saved.captured.hardwareVersion)
        assertEquals("http://old", saved.captured.connectionRequestUrl)
    }

    @Test
    fun `upsert sets lab from ACS tags`() {
        every { client.listTags("new-device") } returns listOf("sub-42", "lab")
        service.upsertFromProvision(
            subscriptionId = 42,
            outcome = Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.COMPLETE,
                deviceId = "new-device",
                acsSnapshot = Tr069AcsSnapshot(serialSuffix = "31C0B6"),
            ),
            smartoltSerial = "VSOL0031C0B6",
        )
        assertTrue(saved.captured.lab)
    }

    @Test
    fun `upsert keeps prior lab when tags cannot be listed`() {
        val existing = SubscriptionAcs(subscriptionId = 42, lab = true)
        every { repository.findById(42) } returns Optional.of(existing)
        every { client.listTags("new-device") } throws RuntimeException("nbi down")
        service.upsertFromProvision(
            subscriptionId = 42,
            outcome = Tr069ProvisionOutcome(
                status = Tr069ProvisionStatus.COMPLETE,
                deviceId = "new-device",
                acsSnapshot = Tr069AcsSnapshot(serialSuffix = "31C0B6"),
            ),
            smartoltSerial = null,
        )
        assertTrue(saved.captured.lab)
    }

    @Test
    fun `syncLabFromDevice persists lab flag`() {
        val existing = SubscriptionAcs(subscriptionId = 42, genieacsDeviceId = "dev", lab = false)
        every { repository.findById(42) } returns Optional.of(existing)
        every { client.listTags("dev") } returns listOf("lab")
        service.syncLabFromDevice(42, "dev")
        assertTrue(saved.captured.lab)
        every { client.listTags("dev") } returns listOf("sub-1")
        service.syncLabFromDevice(42, "dev")
        assertFalse(saved.captured.lab)
    }
}
