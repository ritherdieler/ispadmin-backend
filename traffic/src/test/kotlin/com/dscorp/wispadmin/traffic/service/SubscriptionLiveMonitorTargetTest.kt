package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.port.TrafficDirectoryTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionLiveMonitorTargetTest {

    @Test
    fun `a start command with only ip does not merge a directory username`() {
        val directory = TrafficDirectoryTarget(
            subscriptionId = 5,
            ip = "",
            routerHint = 8,
            pppoeUsername = "gf5",
        )

        val target = SubscriptionLiveMonitorTarget.resolve(
            subscriptionId = 5,
            request = mapOf("subscriptionId" to 5, "ip" to "192.168.250.20"),
            directory = directory,
        )

        assertEquals("192.168.250.20", target?.ip)
        assertNull(target?.pppoeUsername)
        assertEquals(8, target?.routerHint)
        assertEquals("192.168.250.20", TrafficTargetKey.of(target?.ip, target?.pppoeUsername))
    }

    @Test
    fun `a mixed start command uses the directory identity of one access mode`() {
        val directory = TrafficDirectoryTarget(
            subscriptionId = 5,
            ip = "192.168.250.20",
            routerHint = 8,
            pppoeUsername = null,
        )

        val target = SubscriptionLiveMonitorTarget.resolve(
            subscriptionId = 5,
            request = mapOf(
                "subscriptionId" to 5,
                "ip" to "192.168.250.20",
                "pppoeUsername" to "gf5",
            ),
            directory = directory,
        )

        assertEquals("192.168.250.20", target?.ip)
        assertNull(target?.pppoeUsername)
    }

    @Test
    fun `directory static ip is used when the start command only has the id`() {
        val directory = TrafficDirectoryTarget(
            subscriptionId = 5,
            ip = "192.168.250.20",
            routerHint = 8,
            pppoeUsername = null,
        )

        val target = SubscriptionLiveMonitorTarget.resolve(
            subscriptionId = 5,
            request = mapOf("subscriptionId" to 5),
            directory = directory,
        )

        assertEquals("192.168.250.20", target?.ip)
        assertNull(target?.pppoeUsername)
    }

    @Test
    fun `missing identity cannot start a live monitor`() {
        val target = SubscriptionLiveMonitorTarget.resolve(
            subscriptionId = 5,
            request = mapOf("subscriptionId" to 5),
            directory = null,
        )

        assertNull(target)
    }
}
