package com.dscorp.wispadmin.traffic.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrafficSubscriptionPurgeServiceTest {
    @Test
    fun `borra por id, por IP y por usuario PPPoE`() {
        val ids = mutableListOf<Int>()
        val ips = mutableListOf<String>()
        val service = TrafficSubscriptionPurgeService(
            deleteBySubscriptionId = { ids += it },
            deleteByClientIp = { ips += it },
        )

        service.purge(42, "10.0.0.8", "gf42")
        service.purge(42, null, "  ")

        assertEquals(listOf(42, 42), ids)
        assertEquals(listOf("10.0.0.8", "pppoe:gf42"), ips)
    }
}
