package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionHealthRef
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class IdentityServiceTest {
    private val directory = mockk<SubscriptionDirectoryPort>()
    private val service = IdentityService(
        directory,
        emptyProvider<HealthOnuPort>(),
        mockk<IdentityLinkRepository>(),
        mockk<IdentityConflictRepository>(relaxed = true),
        emptyProvider<HealthTrafficPort>(),
        ObjectMapper(),
    )

    private fun <T : Any> emptyProvider(): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns null
        return provider
    }

    private fun ref(
        id: Int,
        tr069DeviceId: String? = null,
        onuSn: String? = null,
        hostDeviceId: Int? = null,
        pppoeUsername: String? = null,
    ) = SubscriptionHealthRef(
        id = id,
        onuSn = onuSn,
        ip = null,
        vlan = null,
        hostDeviceId = hostDeviceId,
        planId = null,
        planDownloadMbps = null,
        planUploadMbps = null,
        napBoxId = null,
        serviceStatus = "ACTIVE",
        tr069DeviceId = tr069DeviceId,
        pppoeUsername = pppoeUsername,
    )

    @Test
    fun `canonical device collision is rejected`() {
        every { directory.findIdsByTr069DeviceId("d") } returns listOf(1, 2)
        assertNull(service.resolveAcs("d"))
    }

    @Test
    fun `operational fallback is accepted only when canonical data does not contradict it`() {
        every { directory.findIdsByTr069DeviceId("d") } returns listOf(1)
        every { directory.find(1) } returns ref(1, tr069DeviceId = "d")
        assertEquals(1, service.resolveAcs("d"))
        every { directory.find(1) } returns ref(1, tr069DeviceId = "another-device")
        assertNull(service.resolveAcs("d"))
    }

    @Test
    fun `unmapped ACS devices cannot manufacture a subscription from tags or serial`() {
        every { directory.findIdsByTr069DeviceId(any()) } returns emptyList()
        assertNull(service.resolveAcs("sub-1-device"))
        verify(exactly = 0) { directory.find(any()) }
    }

    @Test
    fun `snapshot includes PPPoE username without requiring IP`() {
        val map = service.snapshot(ref(6, hostDeviceId = 8, pppoeUsername = "gf6"))
        assertEquals("gf6", map["PPPOE"])
        assertEquals("8", map["ROUTER"])
        assertNull(map["IP"])
    }

    @Test
    fun `resolveOnu maps VSOL inventory SN to Genie fiber serial via hex suffix`() {
        every { directory.findIdsByOnuSerial("VSOL0031C0B6") } returns emptyList()
        every { directory.findIdsByOnuSerialOrSuffix("VSOL0031C0B6", "31C0B6") } returns listOf(2329)
        assertEquals(2329, service.resolveOnu("VSOL0031C0B6"))
    }
}
