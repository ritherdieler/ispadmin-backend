package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

class SyslogIngestAdapterTest {

    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val signalExtractor = mockk<AlertSignalExtractor>()
    private val alertEvaluator = mockk<AlertEvaluator>()
    private val properties = NetDiagProperties().apply {
        syslog.pppMassThreshold = 3
        syslog.pppMassWindowSeconds = 60
    }
    private val adapter = SyslogIngestAdapter(
        targetRepository = targetRepository,
        signalExtractor = signalExtractor,
        alertEvaluator = alertEvaluator,
        properties = properties
    )

    @Test
    fun `detecta loop-protect y correlaciona incidente`() {
        every { targetRepository.findById(1L) } returns Optional.of(
            NetDiagTarget(id = 1L, name = "MK1", deviceRefId = 7L)
        )
        val signal = AlertSignal(
            reasonCode = "LOOP_PROTECT_TRIGGERED",
            severity = "P0",
            title = "Loop protect triggered",
            dedupKey = "LOOP_PROTECT_TRIGGERED:1:bridge1"
        )
        every {
            signalExtractor.fromIngest(1L, "LOOP_PROTECT_TRIGGERED", "P0", any(), "bridge1", any())
        } returns signal
        every { alertEvaluator.evaluateIngest(1L, listOf(signal)) } returns AlertEvaluationResult(
            decisions = listOf("OPEN"),
            openedIncidentIds = listOf(10L)
        )

        val result = adapter.ingest(
            targetId = 1L,
            message = "bridge loop-protect: interface ether5 disabled on bridge1"
        )

        assertEquals(listOf("OPEN"), result.decisions)
        verify { alertEvaluator.evaluateIngest(1L, listOf(signal)) }
    }

    @Test
    fun `detecta link down y high temperature en syslog`() {
        assertEquals(
            "LINK_FLAP",
            adapter.classify("interface ether1 link down")!!.reasonCode
        )
        assertEquals(
            "HIGH_TEMPERATURE",
            adapter.classify("system health: temperature critical 85C")!!.reasonCode
        )
    }

    @Test
    fun `PPP mass disconnect abre PPP_MASS_DISCONNECT tras umbral`() {
        every { targetRepository.findById(1L) } returns Optional.of(
            NetDiagTarget(id = 1L, name = "MK1", deviceRefId = 7L)
        )
        val signal = AlertSignal(
            reasonCode = "PPP_MASS_DISCONNECT",
            severity = "P0",
            title = "PPP mass disconnect",
            dedupKey = "PPP_MASS_DISCONNECT:1:ppp"
        )
        every {
            signalExtractor.fromIngest(1L, "PPP_MASS_DISCONNECT", "P0", any(), "ppp", any())
        } returns signal
        every { alertEvaluator.evaluateIngest(1L, listOf(signal)) } returns AlertEvaluationResult(
            decisions = listOf("OPEN"),
            openedIncidentIds = listOf(22L)
        )

        repeat(2) {
            val r = adapter.ingest(1L, "pppoe-user123: disconnected")
            assertTrue(r.decisions.isEmpty())
        }
        val third = adapter.ingest(1L, "pppoe-user999: disconnected")
        assertEquals(listOf("OPEN"), third.decisions)
    }
}
