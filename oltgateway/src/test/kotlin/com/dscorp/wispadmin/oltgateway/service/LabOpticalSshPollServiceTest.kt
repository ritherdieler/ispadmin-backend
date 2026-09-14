package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.dto.OltOnuRefDto
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher

class LabOpticalSshPollServiceTest {
    private val onuQuery = mockk<OltHealthOnuQueryService>()
    private val cliBus = mockk<OltCliBus>()
    private val cliBusProvider = mockk<ObjectProvider<OltCliBus>>()
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = LabOpticalSshPollService(
        onuQuery,
        cliBusProvider,
        OpticalInfoParser(),
        publisher,
    )

    @Test
    fun `pollAllLab no usa directorio del core`() {
        val result = service.pollAllLab()
        assertEquals("no_directory", result.error)
        verify(exactly = 0) { cliBus.execute<Any>(any(), any()) }
    }

    @Test
    fun `refreshBySn polls the requested onu over ssh`() {
        every { onuQuery.findBySn("12345B4641531C0B6") } returns OltOnuRefDto(
            id = 7627L,
            sn = "VSOL0031C0B6",
            externalId = "a",
            oltId = 2L,
            oltName = "olt",
            board = 1,
            port = 6,
            onuIndex = 10,
        )
        every { cliBusProvider.ifAvailable } returns cliBus
        every { cliBus.execute<Any>(CliJobType.ADHOC, any()) } answers {
            val block = secondArg<(HuaweiCliSession) -> Any>()
            val session = mockk<HuaweiCliSession>()
            every { session.execute(any()) } answers {
                val cmd = firstArg<String>()
                if (cmd.startsWith("display ont optical-info")) detailOutput() else "ok"
            }
            CliBusResult.Ok(block(session))
        }

        val result = service.refreshBySn("12345B4641531C0B6")

        assertTrue(result.collected)
        verify(exactly = 1) { cliBus.execute<Any>(CliJobType.ADHOC, any()) }
        val published = slot<OltOpticalObservation>()
        verify { publisher.publishEvent(capture(published)) }
        assertEquals(2L, published.captured.oltId)
        assertEquals(listOf(10), published.captured.rows.map { it.optical.ontId })
    }

    @Test
    fun `refreshBySn without sn degrades`() {
        val result = service.refreshBySn("  ")
        assertEquals("missing_sn", result.error)
        verify(exactly = 0) { cliBus.execute<Any>(any(), any()) }
    }

    private fun detailOutput() = """
        Rx optical power(dBm)          : -21.50
        Tx optical power(dBm)          : 2.10
        OLT Rx ONT optical power(dBm)  : -27.00
        Temperature(C)                 : 45
        Voltage(V)                     : 3.30
        Laser bias current(mA)         : 12
    """.trimIndent()
}
