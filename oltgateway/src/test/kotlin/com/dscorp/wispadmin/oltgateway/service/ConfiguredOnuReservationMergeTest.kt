package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuFilter
import com.dscorp.wispadmin.oltgateway.dto.ConfiguredOnuItemDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfiguredOnuReservationMergeTest {

    @Test
    fun `incluye reserva CLAIMED sin inventario y la oculta si se pide online`() {
        val inventory = listOf(item(sn = "ONTONLINE", externalId = "ext-1", runState = "online", authorizationDate = "2026-01-02"))
        val claimed = reservation("HWTCRESERVED", stage = "CLAIMED")
        val ready = reservation("ONTONLINE", stage = "READY", externalId = "ext-1")

        val all = ConfiguredOnuReservationMerge.apply(inventory, listOf(claimed, ready), ConfiguredOnuFilter(), 0, 50)
        assertEquals(listOf("ONTONLINE", "HWTCRESERVED"), all.items.map { it.sn })
        assertEquals("READY", all.items[0].reservationStage)
        assertEquals("", all.items[1].externalId)
        assertEquals("CLAIMED", all.items[1].reservationStage)

        val online = ConfiguredOnuReservationMerge.apply(
            inventory,
            listOf(claimed, ready),
            ConfiguredOnuFilter(runState = "online"),
            0,
            50,
        )
        assertEquals(listOf("ONTONLINE"), online.items.map { it.sn })
    }

    @Test
    fun `filtra por etapa y no mezcla otra reserva`() {
        val page = ConfiguredOnuReservationMerge.apply(
            listOf(item("KEPT", "ext-kept")),
            listOf(reservation("KEPT", "AUTHORIZED", "ext-kept"), reservation("OTHER", "CLAIMED")),
            ConfiguredOnuFilter(reservationStage = "CLAIMED"),
            0,
            50,
        )
        assertEquals(listOf("OTHER"), page.items.map { it.sn })
        assertTrue(page.items.none { it.sn == "KEPT" })
    }

    private fun item(
        sn: String,
        externalId: String,
        runState: String? = null,
        authorizationDate: String? = null,
    ) = ConfiguredOnuItemDto(
        id = 1L,
        sn = sn,
        externalId = externalId,
        board = 1,
        port = 0,
        onuIndex = 1,
        name = sn,
        importedFromOlt = true,
        runState = runState,
        matchState = null,
        polledAt = null,
        authorizationDate = authorizationDate,
    )

    private fun reservation(sn: String, stage: String, externalId: String? = null) = ProvisioningV2OnuOwnership(
        serial = sn,
        operationId = "op-$sn",
        callerEnv = "staging",
        requestHash = "hash",
        externalId = externalId,
        board = 1,
        port = 2,
        ontId = null,
        stage = stage,
    )
}
