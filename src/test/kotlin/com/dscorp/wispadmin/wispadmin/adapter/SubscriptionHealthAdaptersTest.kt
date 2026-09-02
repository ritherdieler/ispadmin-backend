package com.dscorp.wispadmin.wispadmin.adapter

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

class SubscriptionHealthAdaptersTest {

    private val acsRepository = mockk<SubscriptionAcsRepository>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val acsAdapter = AcsSubscriptionAdapter(acsRepository)
    private val directoryAdapter = SubscriptionDirectoryAdapter(subscriptionRepository)

    @Test
    fun `el registro ACS canonico es subscription_acs`() {
        every { acsRepository.findById(7) } returns Optional.of(
            SubscriptionAcs(
                subscriptionId = 7,
                genieacsDeviceId = "202BC1-BM632w-0001",
                productClass = "HG8145V5",
                manufacturer = "HUAWEI",
                softwareVersion = "V5R020",
                lastInformAt = LocalDateTime.of(2026, 9, 1, 10, 0),
                lab = true
            )
        )

        val entry = acsAdapter.find(7)

        assertEquals("202BC1-BM632w-0001", entry?.deviceId)
        assertEquals("HG8145V5", entry?.productClass)
        assertEquals("HUAWEI", entry?.manufacturer)
        assertEquals("V5R020", entry?.softwareVersion)
        assertTrue(entry?.lab == true)
        assertEquals("202BC1-BM632w-0001", acsAdapter.findDeviceId(7))
    }

    @Test
    fun `sin registro ACS no hay deviceId ni lab`() {
        every { acsRepository.findById(8) } returns Optional.empty()

        assertNull(acsAdapter.find(8))
        assertNull(acsAdapter.findDeviceId(8))
        assertFalse(acsAdapter.isLab(8))
        assertFalse(acsAdapter.isLab(null))
    }

    @Test
    fun `resuelve suscripciones por deviceId y lista las de laboratorio`() {
        every { acsRepository.findByGenieacsDeviceId("dev-1") } returns listOf(
            SubscriptionAcs(subscriptionId = 3, genieacsDeviceId = "dev-1"),
            SubscriptionAcs(subscriptionId = 4, genieacsDeviceId = "dev-1")
        )
        every { acsRepository.findByLabIsTrue() } returns listOf(
            SubscriptionAcs(subscriptionId = 9, lab = true)
        )

        assertEquals(listOf(3, 4), acsAdapter.findSubscriptionIdsByDeviceId("dev-1"))
        assertEquals(listOf(9), acsAdapter.labSubscriptionIds())
    }

    @Test
    fun `registra el inform sobre el registro canonico`() {
        val existing = SubscriptionAcs(subscriptionId = 7, genieacsDeviceId = "dev-1")
        every { acsRepository.findById(7) } returns Optional.of(existing)
        val saved = slot<SubscriptionAcs>()
        every { acsRepository.save(capture(saved)) } answers { firstArg() }

        val inform = LocalDateTime.of(2026, 9, 1, 12, 30)
        acsAdapter.recordInform(7, inform, "HG8145V5", "V5R021", inform.plusSeconds(1))

        assertEquals(inform, saved.captured.lastInformAt)
        assertEquals("HG8145V5", saved.captured.productClass)
        assertEquals("V5R021", saved.captured.softwareVersion)
    }

    @Test
    fun `no crea registro ACS cuando la suscripcion no lo tiene`() {
        every { acsRepository.findById(11) } returns Optional.empty()

        acsAdapter.recordInform(11, LocalDateTime.now(), "m", "f", LocalDateTime.now())

        verify(exactly = 0) { acsRepository.save(any()) }
    }

    @Test
    fun `el directorio proyecta solo lo que necesita servicehealth`() {
        val subscription = Subscription(
            id = 5,
            ip = "10.20.0.5",
            vlan = "100",
            fiberOnuSn = "HWTC0086CD49",
            plan = Plan(id = 2, downloadSpeed = 200, uploadSpeed = 100),
            hostDevice = NetworkDevice(id = 4),
            napBox = NapBox(id = 6),
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        )
        every { subscriptionRepository.findById(5) } returns Optional.of(subscription)

        val ref = directoryAdapter.find(5)

        assertEquals(5, ref?.id)
        assertEquals("HWTC0086CD49", ref?.onuSn)
        assertEquals("10.20.0.5", ref?.ip)
        assertEquals("100", ref?.vlan)
        assertEquals(4, ref?.hostDeviceId)
        assertEquals(2, ref?.planId)
        assertEquals(200, ref?.planDownloadMbps)
        assertEquals(100, ref?.planUploadMbps)
        assertEquals(6, ref?.napBoxId)
        assertEquals("ACTIVE", ref?.serviceStatus)
    }

    @Test
    fun `el directorio resuelve identidad ONU por serial exacto y por sufijo`() {
        every { subscriptionRepository.findByExactOnuSerial("HWTC0086CD49") } returns
            listOf(Subscription(id = 5, equipmentCondition = EquipmentCondition.LOAN))
        every { subscriptionRepository.findByOnuSerialOrSuffix("0086CD49", "0086CD49") } returns
            listOf(
                Subscription(id = 5, equipmentCondition = EquipmentCondition.LOAN),
                Subscription(id = 6, equipmentCondition = EquipmentCondition.LOAN)
            )

        assertEquals(listOf(5), directoryAdapter.findIdsByOnuSerial("HWTC0086CD49"))
        assertEquals(listOf(5, 6), directoryAdapter.findIdsByOnuSerialOrSuffix("0086CD49", "0086CD49"))
    }

    @Test
    fun `el directorio expone existencia listado e id de identidad bloqueado`() {
        every { subscriptionRepository.findAllIds() } returns listOf(1, 2, 3)
        every { subscriptionRepository.existsById(2) } returns true
        every { subscriptionRepository.lockIdentityOwner(2) } returns Subscription(id = 2, equipmentCondition = EquipmentCondition.LOAN)

        assertEquals(listOf(1, 2, 3), directoryAdapter.allIds())
        assertTrue(directoryAdapter.exists(2))
        assertEquals(2, directoryAdapter.lockIdentityOwner(2)?.id)
    }
}
