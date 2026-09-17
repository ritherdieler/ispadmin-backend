package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficLiveTickDto
import com.dscorp.wispadmin.traffic.entity.TrafficRouter
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryPort
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SubscriptionTrafficLiveMonitorTest {

    @Test
    fun `in-process start emits live ticks from the simple queue without a STOMP loopback`() {
        val directory = mockk<TrafficDirectoryPort>()
        val routers = mockk<TrafficRouterRepository>()
        val mikrotik = mockk<MikrotikClient>()
        val session = mockk<MikrotikSession>()
        every { directory.list() } returns emptyList()
        every { routers.findById(8) } returns Optional.of(
            TrafficRouter(id = 8, name = "MK2", host = "38.224.231.4", username = "u", password = "p"),
        )
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf(
                "name" to "[stg] id:5",
                "target" to "192.168.250.20/32",
                "bytes" to "148240/148716",
                "rate" to "11200/11200",
            ),
        )
        every {
            mikrotik.withSession(any<MikrotikDeviceRef>(), any<(MikrotikSession) -> Any>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val block = invocation.args[1] as (MikrotikSession) -> Any
            block(session)
        }

        val ticks = CopyOnWriteArrayList<Any>()
        val latch = CountDownLatch(1)
        val monitor = SubscriptionTrafficLiveMonitor(
            directory,
            routers,
            mikrotik,
            RouterOsClientProperties(),
            SimpleQueueSnapshotCache(),
        )
        try {
            monitor.start(
                mapOf("subscriptionId" to 5, "ip" to "192.168.250.20", "routerHint" to 8),
            ) { tick ->
                ticks.add(tick)
                latch.countDown()
            }
            assertTrue(latch.await(3, TimeUnit.SECONDS), "expected an in-process live tick")
            val tick = ticks.first() as SubscriptionTrafficLiveTickDto
            assertEquals(5, tick.subscriptionId)
        } finally {
            monitor.stop(5)
            monitor.close()
        }
    }
}
