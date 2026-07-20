package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.mapper.SmartOltCompatMapper
import com.dscorp.wispadmin.oltgateway.parser.AutofindParser
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.FixtureLoader
import com.dscorp.wispadmin.oltgateway.parser.OnuInfoBySnParser
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.parser.VersionParser
import com.dscorp.wispadmin.oltgateway.service.inventory.ParallelOnuInventoryReader
import com.dscorp.wispadmin.oltgateway.ssh.HuaweiCliSession
import com.dscorp.wispadmin.oltgateway.ssh.OltCommandExecutor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

class OltGatewayQueryServiceTest {

    private val commandExecutor = mockk<OltCommandExecutor>()
    private val inventoryReader = mockk<ParallelOnuInventoryReader>()
    private val oltRepository = mockk<OltMgrOltRepository>()
    private val properties = OltGatewayProperties().apply {
        oltId = "gigafiber-ma5608t"
        modelCode = "MA5608T"
        session.poolSize = 1
    }
    private val service = OltGatewayQueryService(
        commandExecutor = commandExecutor,
        inventoryReader = inventoryReader,
        oltRepository = oltRepository,
        smartOltCompatMapper = SmartOltCompatMapper(),
        properties = properties,
        versionParser = VersionParser(),
        boardParser = BoardParser(),
        autofindParser = AutofindParser(),
        onuInfoBySnParser = OnuInfoBySnParser(),
        opticalInfoParser = OpticalInfoParser()
    )

    @Test
    fun `autofind orquesta CLI y mapea SmartOLT`() {
        every { commandExecutor.run("display ont autofind all") } returns FixtureLoader.load("display-ont-autofind-all.txt")

        val result = service.autofind()

        assertTrue(result.status)
        assertEquals(2, result.response.size)
        assertEquals("4857544311E70E9A", result.response[0].sn)
        assertEquals("0", result.response[0].board)
        assertEquals("2", result.response[0].port)
        verify(exactly = 1) { commandExecutor.run("display ont autofind all") }
    }

    @Test
    fun `bySn orquesta CLI y mapea detalles`() {
        every { commandExecutor.run("display ont info by-sn 4857544311E70E9A") } returns
            FixtureLoader.load("display-ont-info-by-sn.txt")

        val result = service.bySn("4857544311E70E9A")

        assertTrue(result.status)
        assertEquals("4857544311E70E9A", result.onus[0].sn)
        assertEquals("1", result.onus[0].board)
        assertEquals("5", result.onus[0].onu)
        assertEquals("gigafiber-ma5608t_1_0_5", result.onus[0].unique_external_id)
        verify(exactly = 1) { commandExecutor.run("display ont info by-sn 4857544311E70E9A") }
    }

    @Test
    fun `health usa ping del bus via executor`() {
        every { commandExecutor.ping() } returns 42L

        val result = service.health()

        assertEquals("UP", result.status)
        assertTrue(result.oltReachable)
        assertEquals(42L, result.latencyMs)
    }

    @Test
    fun `listOnus delega al inventory reader`() {
        every { inventoryReader.listOnusParsed() } returns listOf(
            ParsedOnuSummary(
                frame = 0,
                slot = 1,
                port = 4,
                ontId = 0,
                sn = "4857544315F5B806",
                controlFlag = "active",
                runState = "online",
                configState = "normal",
                matchState = "match",
                description = "Some customer name here"
            )
        )

        val result = service.listOnus()

        assertEquals(1, result.total)
        assertEquals("4857544315F5B806", result.items[0].sn)
        assertEquals(1, result.items[0].slot)
        assertEquals(4, result.items[0].port)
        verify(exactly = 1) { inventoryReader.listOnusParsed() }
    }

    @Test
    fun `listOnusParsed delega al inventory reader`() {
        every { inventoryReader.listOnusParsed() } returns listOf(
            ParsedOnuSummary(
                frame = 0,
                slot = 1,
                port = 4,
                ontId = 0,
                sn = "4857544315F5B806",
                controlFlag = "active",
                runState = "online",
                configState = "normal",
                matchState = "match"
            )
        )

        val result = service.listOnusParsed()

        assertEquals(1, result.size)
        assertEquals("online", result[0].runState)
        verify(exactly = 1) { inventoryReader.listOnusParsed() }
    }

    @Test
    fun `optical parsea formato detalle KV via CLI adhoc`() {
        val session = mockk<HuaweiCliSession>()
        val detailOutput = FixtureLoader.load("display-ont-optical-info-detail-live.txt")
        every { session.execute("interface gpon 0/1") } returns "ok"
        every { session.execute("display ont optical-info 7 0") } returns detailOutput
        every { session.execute("quit") } returns "ok"
        every { commandExecutor.adhoc(any<(HuaweiCliSession) -> Any>()) } answers {
            val block = firstArg<(HuaweiCliSession) -> Any>()
            block(session)
        }

        val result = service.optical(slot = 1, port = 7, ontId = 0)

        assertEquals(1, result.slot)
        assertEquals(7, result.port)
        assertEquals(0, result.ontId)
        assertEquals(-20.40, result.rxPowerDbm!!, 0.001)
        assertEquals(2.17, result.txPowerDbm!!, 0.001)
        assertEquals(-24.95, result.oltRxPowerDbm!!, 0.001)
        assertEquals(37.0, result.temperatureC!!, 0.01)
        assertEquals(3.220, result.voltageV!!, 0.001)
        assertEquals(10.0, result.biasCurrentMa!!, 0.001)
    }

    @Test
    fun `oltInfo incluye modelCode y max sesiones`() {
        val session = mockk<HuaweiCliSession>()
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.empty()
        every { session.execute("display version") } returns FixtureLoader.load("display-version.txt")
        every { session.execute(match { it.startsWith("display board ") }) } returns ""
        every { commandExecutor.adhoc(any<(HuaweiCliSession) -> Any>()) } answers {
            val block = firstArg<(HuaweiCliSession) -> Any>()
            block(session)
        }

        val result = service.oltInfo()

        assertEquals("MA5608T", result.modelCode)
        assertEquals(1, result.maxConcurrentCliSessions)
    }
}
