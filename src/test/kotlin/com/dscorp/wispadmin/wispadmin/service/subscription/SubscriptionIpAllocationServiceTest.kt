package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.IpPoolRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SubscriptionIpAllocationServiceTest {

    private lateinit var ipPoolRepository: IpPoolRepository
    private lateinit var service: SubscriptionIpAllocationService

    @BeforeEach
    fun setUp() {
        ipPoolRepository = mock(IpPoolRepository::class.java)
        service = SubscriptionIpAllocationService(ipPoolRepository)
    }

    @Test
    fun `allocateFreeIp usa pool del hostDevice indicado`() {
        val hostDevice = NetworkDevice(id = 8, name = "MK2")
        val pool = IpPool(
            id = 2,
            ipSegment = "192.168.30.1/24",
            hostDevice = hostDevice,
            ips = listOf(
                Subscription(ip = "192.168.30.10", equipmentCondition = EquipmentCondition.LOAN),
                Subscription(ip = "192.168.30.11", equipmentCondition = EquipmentCondition.LOAN)
            )
        )
        `when`(ipPoolRepository.findEligiblePoolsByHostDeviceId(8)).thenReturn(listOf(pool))

        val (ip, selectedPool) = service.allocateFreeIp(8)

        assertEquals("192.168.30.12", ip)
        assertEquals(pool, selectedPool)
    }

    @Test
    fun `allocateFreeIp falla cuando no hay pool elegible para el hostDevice`() {
        `when`(ipPoolRepository.findEligiblePoolsByHostDeviceId(8)).thenReturn(emptyList())

        assertThrows(Exception::class.java) {
            service.allocateFreeIp(8)
        }
    }

    @Test
    fun `allocateFreeIp falla cuando el pool del hostDevice esta lleno`() {
        val hostDevice = NetworkDevice(id = 8)
        val usedIps = (10..250).map {
            Subscription(ip = "192.168.30.$it", equipmentCondition = EquipmentCondition.LOAN)
        }
        val pool = IpPool(
            id = 2,
            ipSegment = "192.168.30.1/24",
            hostDevice = hostDevice,
            ips = usedIps
        )
        `when`(ipPoolRepository.findEligiblePoolsByHostDeviceId(8)).thenReturn(listOf(pool))

        assertThrows(Exception::class.java) {
            service.allocateFreeIp(8)
        }
    }
}
