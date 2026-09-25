package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.OnuOpticalBatchItem
import com.dscorp.wispadmin.events.OnuOpticalBatchPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class OpticalFanoutTest {

    @Test
    fun `la copia stg solo incluye seriales lab y prod conserva la flota`() {
        val payload = batch("ZTEGDC47BFFD", "HWTC12345678")

        val copies = opticalPayloadsByNamespace(payload, listOf("prod", "stg")) { sn ->
            sn == "ZTEGDC47BFFD"
        }

        assertEquals(listOf("prod", "stg"), copies.map { it.first })
        assertEquals(listOf("ZTEGDC47BFFD", "HWTC12345678"), copies[0].second.onus.map { it.sn })
        assertEquals(listOf("ZTEGDC47BFFD"), copies[1].second.onus.map { it.sn })
    }

    @Test
    fun `stg sin serial lab no publica copia`() {
        val copies = opticalPayloadsByNamespace(batch("HWTC12345678"), listOf("prod", "stg")) { false }

        assertEquals(listOf("prod"), copies.map { it.first })
        assertEquals(listOf("HWTC12345678"), copies.single().second.onus.map { it.sn })
    }

    private fun batch(vararg serials: String) = OnuOpticalBatchPayload(
        oltId = 1,
        slot = 0,
        port = 1,
        polledAt = Instant.parse("2026-09-25T00:00:00Z"),
        onus = serials.map { sn ->
            OnuOpticalBatchItem(
                sn = sn,
                onuExternalId = sn,
                onuRxDbm = -20.0,
                onuTxDbm = 2.0,
                oltRxDbm = -18.0,
                polledAt = Instant.parse("2026-09-25T00:00:00Z"),
            )
        },
    )
}
