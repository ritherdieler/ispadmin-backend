package com.dscorp.wispadmin.acs.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AcsSubscriptionPurgeServiceTest {
    @Test
    fun `sin serial ni device no borra nada`() {
        val calls = mutableListOf<String>()
        val service = AcsSubscriptionPurgeService(
            deleteTasks = { calls += "tasks" },
            deleteCpe = { calls += "cpe" },
            purgeDevice = { calls += "device" },
        )

        service.purge(null, "  ")

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `un segundo purge del mismo serial sigue siendo exito`() {
        val gone = mutableSetOf<String>()
        val service = AcsSubscriptionPurgeService(
            deleteTasks = { sn -> gone += "task:$sn" },
            deleteCpe = { sn -> gone += "cpe:$sn" },
            purgeDevice = { id -> gone += "device:$id" },
        )

        service.purge("ZTEG1", "dev-1")
        service.purge("ZTEG1", "dev-1")

        assertEquals(setOf("task:ZTEG1", "cpe:ZTEG1", "device:dev-1"), gone)
    }

    @Test
    fun `un fallo de GenieACS se propaga para reintentar`() {
        val service = AcsSubscriptionPurgeService(
            deleteTasks = {},
            deleteCpe = {},
            purgeDevice = { throw AcsPurgeException("ACS HTTP 503", retryable = true) },
        )

        val error = assertThrows<AcsPurgeException> { service.purge("ZTEG1", "dev-1") }

        assertEquals(true, error.retryable)
    }
}
