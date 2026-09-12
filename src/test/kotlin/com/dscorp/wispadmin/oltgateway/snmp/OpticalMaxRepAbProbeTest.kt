package com.dscorp.wispadmin.oltgateway.snmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration

class OpticalMaxRepAbProbeTest {

    @Test
    fun `walk records OK pages until end of subtree`() {
        val responses = ArrayDeque(
            listOf(
                GetBulkPageResponse(oids = listOf("1.1.1", "1.1.2"), timedOut = false),
                GetBulkPageResponse(oids = listOf("1.1.3"), timedOut = false),
                GetBulkPageResponse(oids = listOf("1.2.0"), timedOut = false),
            )
        )
        val probe = OpticalMaxRepAbProbe { _, _ -> responses.removeFirst() }
        val report = probe.walk(rootOid = "1.1", maxRepetitions = 15)
        assertEquals(15, report.maxRepetitions)
        assertEquals(2, report.pagesOk)
        assertEquals(0, report.pagesTimedOut)
        assertEquals(3, report.rowCount)
        assertEquals(2, report.pages.size)
        assertTrue(report.pages.all { it.ok })
    }

    @Test
    fun `walk compara cursores como OID numerico no como texto`() {
        val responses = ArrayDeque(
            listOf(
                GetBulkPageResponse(oids = listOf("1.1.7", "1.1.8"), timedOut = false),
                GetBulkPageResponse(oids = listOf("1.1.9", "1.1.23"), timedOut = false),
                GetBulkPageResponse(oids = listOf("1.2.0"), timedOut = false),
            )
        )
        val probe = OpticalMaxRepAbProbe { _, _ -> responses.removeFirst() }

        val report = probe.walk(rootOid = "1.1", maxRepetitions = 15)

        assertEquals(4, report.rowCount)
        assertEquals(2, report.pagesOk)
        assertEquals(0, report.pagesTimedOut)
        assertTrue(report.pages.none { it.error?.contains("non-advancing") == true }, "${report.pages}")
    }

    @Test
    fun `walk records timeout page and stops`() {
        var calls = 0
        val probe = OpticalMaxRepAbProbe { _, _ ->
            calls++
            if (calls == 1) {
                GetBulkPageResponse(oids = listOf("1.1.1"), timedOut = false)
            } else {
                throw IOException("request timed out")
            }
        }
        val report = probe.walk(rootOid = "1.1", maxRepetitions = 10)
        assertEquals(1, report.pagesOk)
        assertEquals(1, report.pagesTimedOut)
        assertEquals(2, report.pages.size)
        assertEquals(false, report.pages.last().ok)
        assertTrue(report.pages.last().error!!.contains("timed out"))
    }

    @Test
    fun `compare runs 15 then 10`() {
        val maxReps = mutableListOf<Int>()
        val probe = OpticalMaxRepAbProbe { _, maxRep ->
            maxReps += maxRep
            GetBulkPageResponse(oids = listOf("9.9.9"), timedOut = false)
        }
        val report = probe.compare(rootOid = "1.1")
        assertEquals(listOf(15, 10), maxReps)
        assertEquals(15, report.first.maxRepetitions)
        assertEquals(10, report.second.maxRepetitions)
    }

    @Test
    fun `port 1 6 uses encoded ifIndex on rx column`() {
        val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot = 1, port = 6)
        val oid = OpticalMaxRepAbProbe.rootOidForPort(slot = 1, port = 6)
        assertEquals("${HuaweiGponSnmpOids.ONT_RX_POWER}.$ifIndex", oid)
    }

    @Test
    fun `ab job holds lock across both walks`() {
        val store = FakeOltSnmpPollLockStore()
        val lock = OltSnmpPollLock(
            store = store,
            key = "olt-snmp-poll",
            ttl = Duration.ofMillis(20_000),
            waitSlice = Duration.ofMillis(1_000),
            clock = { store.nowMs },
            sleeper = { store.nowMs += it },
            ownerId = "ab",
        )
        val held = mutableListOf<Boolean>()
        val probe = OpticalMaxRepAbProbe { _, _ ->
            held += store.isHeldBy("olt-snmp-poll", "ab")
            GetBulkPageResponse(oids = listOf("1.1.1"), timedOut = false)
        }
        OpticalMaxRepAbJob.run(lock, probe, rootOid = "1.1")
        assertTrue(held.isNotEmpty())
        assertTrue(held.all { it })
        assertFalse(store.isHeld("olt-snmp-poll"))
    }
}
