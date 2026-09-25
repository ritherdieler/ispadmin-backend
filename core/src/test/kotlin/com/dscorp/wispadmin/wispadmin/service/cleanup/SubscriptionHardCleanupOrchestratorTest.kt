package com.dscorp.wispadmin.wispadmin.service.cleanup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionHardCleanupOrchestratorTest {
    private val snapshot = CleanupSnapshot(subscriptionId = 42, serial = "ZTEG1", ip = "10.0.0.8", pppoeUsername = "gf42")

    @Test
    fun `un corte transitorio se reintenta y al completar borra el rastro`() {
        val calls = mutableListOf<String>()
        var mikrotik = 0
        val journal = MemoryCleanupJournal(snapshot)
        val eraser = RecordingEraser()
        val orchestrator = orchestrator(
            journal,
            eraser,
            "mikrotik" to { _: CleanupSnapshot ->
                mikrotik++
                if (mikrotik == 1) throw CleanupStepException(CleanupFailure(
                    code = "MIKROTIK_UNAVAILABLE",
                    message = "MikroTik no respondió.",
                    detail = "intento 1",
                    retryable = true,
                ))
                calls += "mikrotik"
            },
            "acs" to { _: CleanupSnapshot -> calls += "acs" },
        )

        val report = orchestrator.execute(42)

        assertEquals("COMPLETE", report.status)
        assertEquals(2, mikrotik)
        assertEquals(listOf("mikrotik", "acs"), calls)
        assertEquals(listOf(42), eraser.erased)
        assertTrue(journal.dropped.contains(42))
    }

    @Test
    fun `un error de negocio no se reintenta y la suscripcion sigue`() {
        var calls = 0
        val journal = MemoryCleanupJournal(snapshot)
        val eraser = RecordingEraser()
        val orchestrator = orchestrator(
            journal,
            eraser,
            "olt" to { _: CleanupSnapshot ->
                calls++
                throw CleanupStepException(CleanupFailure(
                    code = "OLT_REJECTED",
                    message = "No se pudo borrar la ONU. La suscripción sigue en el listado.",
                    detail = "HTTP 409 olt",
                    retryable = false,
                ))
            },
        )

        val report = orchestrator.execute(42)

        assertEquals("PARTIAL", report.status)
        assertEquals(1, calls)
        assertEquals("OLT_REJECTED", report.steps.single().code)
        assertTrue(report.steps.single().message!!.contains("suscripción"))
        assertTrue(eraser.erased.isEmpty())
        assertTrue(journal.dropped.isEmpty())
    }

    @Test
    fun `el segundo intento no repite un paso ya limpio`() {
        val calls = mutableListOf<String>()
        val journal = MemoryCleanupJournal(
            snapshot,
            steps = mapOf("mikrotik" to CleanupStepView(step = "mikrotik", state = "ok")),
        )
        val orchestrator = orchestrator(
            journal,
            RecordingEraser(),
            "mikrotik" to { _: CleanupSnapshot -> calls += "mikrotik" },
            "acs" to { _: CleanupSnapshot -> calls += "acs" },
        )

        val report = orchestrator.execute(42)

        assertEquals("COMPLETE", report.status)
        assertEquals(listOf("acs"), calls)
    }

    @Test
    fun `si queda una fila el borrado no se declara completo`() {
        val journal = MemoryCleanupJournal(snapshot)
        val eraser = RecordingEraser(leftover = "payment")
        val orchestrator = orchestrator(journal, eraser, "acs" to { _: CleanupSnapshot -> })

        val report = orchestrator.execute(42)

        assertEquals("PARTIAL", report.status)
        val core = report.steps.last()
        assertEquals("TRACE_REMAINING", core.code)
        assertEquals("payment", core.detail)
        assertTrue(journal.dropped.isEmpty())
    }

    private fun orchestrator(
        journal: MemoryCleanupJournal,
        eraser: RecordingEraser,
        vararg steps: Pair<String, (CleanupSnapshot) -> Unit>,
    ) = SubscriptionHardCleanupOrchestrator(
        journal = journal,
        steps = steps.toList(),
        eraser = eraser,
        sleeper = {},
    )
}

private class MemoryCleanupJournal(
    snapshot: CleanupSnapshot,
    steps: Map<String, CleanupStepView> = emptyMap(),
) : CleanupJournal {
    var run = CleanupRun(snapshot, steps)
    val dropped = mutableListOf<Int>()

    override fun load(subscriptionId: Int): CleanupRun = run.copy(steps = run.steps.toMap())

    override fun save(updated: CleanupRun) {
        run = updated
    }

    override fun drop(subscriptionId: Int) {
        dropped += subscriptionId
    }
}

private class RecordingEraser(private val leftover: String? = null) : SubscriptionTraceEraser {
    val erased = mutableListOf<Int>()

    override fun erase(snapshot: CleanupSnapshot) {
        erased += snapshot.subscriptionId
        if (leftover != null) {
            throw CleanupStepException(CleanupFailure(
                code = "TRACE_REMAINING",
                message = "Quedó un registro de la suscripción.",
                detail = leftover,
                retryable = true,
            ))
        }
    }
}
