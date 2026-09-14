package com.dscorp.wispadmin.oltgateway.snmp

import org.snmp4j.smi.OID

data class GetBulkPageResponse(
    val oids: List<String>,
    val timedOut: Boolean = false,
)

data class GetBulkPageRecord(
    val pageIndex: Int,
    val ok: Boolean,
    val bindings: Int,
    val error: String? = null,
)

data class MaxRepWalkReport(
    val maxRepetitions: Int,
    val pages: List<GetBulkPageRecord>,
    val rowCount: Int,
) {
    val pagesOk: Int get() = pages.count { it.ok }
    val pagesTimedOut: Int get() = pages.count { !it.ok }
}

data class OpticalMaxRepAbReport(
    val first: MaxRepWalkReport,
    val second: MaxRepWalkReport,
)

class OpticalMaxRepAbProbe(
    private val sendPage: (cursorOid: String, maxRepetitions: Int) -> GetBulkPageResponse,
) {
    fun walk(rootOid: String, maxRepetitions: Int, maxPages: Int = 500): MaxRepWalkReport {
        val pages = mutableListOf<GetBulkPageRecord>()
        var cursor = rootOid
        var rowCount = 0
        var pageIndex = 0
        while (pageIndex < maxPages) {
            pageIndex++
            val response = try {
                sendPage(cursor, maxRepetitions)
            } catch (ex: Exception) {
                pages += GetBulkPageRecord(
                    pageIndex = pageIndex,
                    ok = false,
                    bindings = 0,
                    error = ex.message,
                )
                break
            }
            if (response.timedOut) {
                pages += GetBulkPageRecord(
                    pageIndex = pageIndex,
                    ok = false,
                    bindings = 0,
                    error = "request timed out",
                )
                break
            }
            val inSubtree = response.oids.filter { it == rootOid || it.startsWith("$rootOid.") }
            if (inSubtree.isEmpty()) {
                break
            }
            rowCount += inSubtree.size
            pages += GetBulkPageRecord(
                pageIndex = pageIndex,
                ok = true,
                bindings = inSubtree.size,
            )
            val last = inSubtree.last()
            if (OID(last).compareTo(OID(cursor)) <= 0) {
                pages += GetBulkPageRecord(
                    pageIndex = pageIndex + 1,
                    ok = false,
                    bindings = 0,
                    error = "non-advancing response",
                )
                break
            }
            cursor = last
            if (inSubtree.size < response.oids.size) {
                break
            }
        }
        return MaxRepWalkReport(
            maxRepetitions = maxRepetitions,
            pages = pages,
            rowCount = rowCount,
        )
    }

    fun compare(rootOid: String, firstMaxRep: Int = 15, secondMaxRep: Int = 10): OpticalMaxRepAbReport {
        return OpticalMaxRepAbReport(
            first = walk(rootOid, firstMaxRep),
            second = walk(rootOid, secondMaxRep),
        )
    }

    companion object {
        fun rootOidForPort(slot: Int, port: Int): String {
            val ifIndex = HuaweiGponSnmpCodec.encodeIfIndex(slot, port)
            return "${HuaweiGponSnmpOids.ONT_RX_POWER}.$ifIndex"
        }
    }
}

object OpticalMaxRepAbJob {
    fun run(
        lock: OltSnmpPollLocker,
        probe: OpticalMaxRepAbProbe,
        rootOid: String,
    ): OpticalMaxRepAbReport {
        return lock.withLock { probe.compare(rootOid) }
    }
}
