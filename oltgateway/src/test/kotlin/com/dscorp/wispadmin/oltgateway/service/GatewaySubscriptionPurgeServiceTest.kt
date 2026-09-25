package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GatewaySubscriptionPurgeServiceTest {
    @Test
    fun `una ONU que ya no esta sigue limpiando el inventario`() {
        val inventory = mutableListOf<String>()
        val service = GatewaySubscriptionPurgeService(
            deleteOnu = { throw OnuNotFoundException("missing") },
            deleteInventory = { inventory += it },
        )

        service.purge("zteg1")
        service.purge("zteg1")

        assertEquals(listOf("zteg1", "zteg1"), inventory)
    }

    @Test
    fun `sin serial no llama a la OLT`() {
        var calls = 0
        val service = GatewaySubscriptionPurgeService(
            deleteOnu = { calls++ },
            deleteInventory = { calls++ },
        )

        service.purge("  ")

        assertEquals(0, calls)
    }
}
