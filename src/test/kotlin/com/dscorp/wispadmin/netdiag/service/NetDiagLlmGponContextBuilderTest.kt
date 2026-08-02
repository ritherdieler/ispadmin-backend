package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagOltLogEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagOltLogEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagOntSubscriptionPort
import com.dscorp.wispadmin.netdiag.port.OntSubscriptionInfo
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

class NetDiagLlmGponContextBuilderTest {

    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val oltLogEventRepository = mockk<NetDiagOltLogEventRepository>()
    private val oltRepository = mockk<OltMgrOltRepository>()
    private val onuRepository = mockk<OltMgrOnuRepository>()
    private val ontSubscriptionPort = mockk<NetDiagOntSubscriptionPort>()
    private val builder = NetDiagLlmGponContextBuilder(
        targetRepository = targetRepository,
        oltLogEventRepository = oltLogEventRepository,
        oltRepository = oltRepository,
        onuRepository = onuRepository,
        ontSubscriptionPort = ontSubscriptionPort,
        objectMapper = ObjectMapper()
    )

    private val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.20.30.2")

    private fun onu(
        index: Int,
        sn: String,
        runState: String?,
        lastDownCause: String? = null,
        rx: BigDecimal? = null
    ): OltMgrOnu {
        val onu = OltMgrOnu(id = index.toLong(), sn = sn, olt = olt, board = 0, port = 0, onuIndex = index)
        if (runState != null) {
            onu.status = OltMgrOnuStatusCurrent(
                onuId = onu.id,
                onu = onu,
                runState = runState,
                lastDownCause = lastDownCause,
                onuRxDbm = rx
            )
        }
        return onu
    }

    private fun ponTarget(): NetDiagTarget = NetDiagTarget(
        id = 4L,
        name = "PON-gigafiber-ma5608t-gpon-0/0",
        deviceRefId = 1_001_000L,
        parentTargetId = 3L,
        monitorConfig = """{"kind":"pon","oltId":"gigafiber-ma5608t","board":0,"port":0}"""
    )

    private fun ponIncident(target: NetDiagTarget = ponTarget()): NetDiagIncident = NetDiagIncident(
        id = 387L,
        target = target,
        dedupKey = "ONT_CONFIG_RECOVERY_FAIL:4:gpon-0/0:ont-16",
        status = "OPEN",
        severity = "P2",
        title = "ONT_CONFIG_RECOVERY_FAIL gpon-0/0 ont=16",
        reasonCode = "ONT_CONFIG_RECOVERY_FAIL",
        openedAt = Instant.parse("2026-08-01T23:03:13Z")
    )

    @Test
    fun `target mikrotik retorna null`() {
        val target = NetDiagTarget(id = 1L, name = "MK1", deviceRefId = 7L, monitorConfig = null)
        val incident = NetDiagIncident(id = 42L, target = target, dedupKey = "LINK_DOWN:1:ether1")

        assertNull(builder.build(incident))
    }

    @Test
    fun `target pon arma contexto completo con parent logs inventario y abonado`() {
        every { targetRepository.findById(3L) } returns Optional.of(
            NetDiagTarget(
                id = 3L,
                name = "OLT-gigafiber-ma5608t",
                deviceRefId = 1L,
                monitorConfig = """{"kind":"olt","oltId":"gigafiber-ma5608t","mgmtIp":"10.20.30.2"}"""
            )
        )
        every { oltLogEventRepository.findTop50ByTargetIdOrderByReceivedAtDesc(4L) } returns listOf(
            NetDiagOltLogEvent(
                id = 900L,
                receivedAt = Instant.parse("2026-08-01T23:03:00Z"),
                rawMessage = "x".repeat(1000),
                reasonCode = "ONT_CONFIG_RECOVERY_FAIL",
                board = 0,
                port = 0,
                onuIndex = 16,
                targetId = 4L,
                severity = "P2",
                alarmName = "The GPON ONT configuration recovery fails",
                isClear = false
            )
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        val onus = listOf(
            onu(15, "HWTC00000015", "online"),
            onu(16, "HWTC00000016", "online", rx = BigDecimal("-18.42")),
            onu(17, "HWTC00000017", "offline", lastDownCause = "dying-gasp", rx = BigDecimal("-21.10"))
        )
        every { onuRepository.findByOlt_IdAndBoardAndPortWithStatus(1L, 0, 0) } returns onus
        every {
            onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(1L, 0, 0, 16)
        } returns Optional.of(onus[1])
        every { ontSubscriptionPort.findActiveByOnuSn("HWTC00000016") } returns OntSubscriptionInfo(
            subscriptionId = 555,
            customerName = "Juan Perez",
            serviceStatus = "ACTIVE",
            napBoxCode = "NAP-05"
        )

        val context = builder.build(ponIncident())

        assertNotNull(context)
        context!!
        assertEquals("pon", context.targetContext.kind)
        assertEquals("gigafiber-ma5608t", context.targetContext.oltId)
        assertEquals(0, context.targetContext.board)
        assertEquals(0, context.targetContext.port)
        assertEquals("OLT-gigafiber-ma5608t", context.targetContext.parentTargetName)

        assertEquals(1, context.recentOltLogs.size)
        val log = context.recentOltLogs.first()
        assertEquals("ONT_CONFIG_RECOVERY_FAIL", log.reasonCode)
        assertEquals(16, log.onuIndex)
        assertEquals("The GPON ONT configuration recovery fails", log.alarmName)
        assertTrue(log.rawMessage.length <= 400)

        val inventory = context.ponInventory
        assertNotNull(inventory)
        assertEquals(3, inventory!!.total)
        assertEquals(2, inventory.online)
        assertEquals(1, inventory.offline)
        assertEquals(1, inventory.offlineSample.size)
        val offline = inventory.offlineSample.first()
        assertEquals(17, offline.onuIndex)
        assertEquals("HWTC00000017", offline.sn)
        assertEquals("dying-gasp", offline.lastDownCause)
        assertEquals(-21.10, offline.onuRxDbm)

        val ont = context.ontSubscription
        assertNotNull(ont)
        assertEquals(16, ont!!.onuIndex)
        assertEquals("HWTC00000016", ont.sn)
        assertEquals("online", ont.runState)
        assertEquals(555, ont.subscription?.subscriptionId)
        assertEquals("Juan Perez", ont.subscription?.customerName)
        verify(exactly = 1) { ontSubscriptionPort.findActiveByOnuSn("HWTC00000016") }
    }

    @Test
    fun `target pon sin ont en dedup key omite abonado`() {
        every { targetRepository.findById(3L) } returns Optional.empty()
        every { oltLogEventRepository.findTop50ByTargetIdOrderByReceivedAtDesc(4L) } returns emptyList()
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { onuRepository.findByOlt_IdAndBoardAndPortWithStatus(1L, 0, 0) } returns emptyList()

        val incident = NetDiagIncident(
            id = 400L,
            target = ponTarget(),
            dedupKey = "PON_PORT_LOS:4:gpon-0/0",
            title = "PON_PORT_LOS gpon-0/0",
            reasonCode = "PON_PORT_LOS"
        )

        val context = builder.build(incident)

        assertNotNull(context)
        assertNull(context!!.ontSubscription)
        assertEquals(0, context.ponInventory?.total)
    }

    @Test
    fun `target olt arma contexto sin inventario ni abonado`() {
        val target = NetDiagTarget(
            id = 3L,
            name = "OLT-gigafiber-ma5608t",
            deviceRefId = 1L,
            monitorConfig = """{"kind":"olt","oltId":"gigafiber-ma5608t","mgmtIp":"10.20.30.2"}"""
        )
        val incident = NetDiagIncident(
            id = 500L,
            target = target,
            dedupKey = "OLT_UNREACHABLE:3",
            title = "OLT unreachable",
            reasonCode = "OLT_UNREACHABLE"
        )
        every { oltLogEventRepository.findTop50ByTargetIdOrderByReceivedAtDesc(3L) } returns emptyList()

        val context = builder.build(incident)

        assertNotNull(context)
        context!!
        assertEquals("olt", context.targetContext.kind)
        assertEquals("10.20.30.2", context.targetContext.mgmtIp)
        assertNull(context.targetContext.parentTargetName)
        assertNull(context.ponInventory)
        assertNull(context.ontSubscription)
    }

    @Test
    fun `recorta logs a 20 entradas`() {
        every { targetRepository.findById(3L) } returns Optional.empty()
        val logs = (1..50).map { i ->
            NetDiagOltLogEvent(
                id = i.toLong(),
                receivedAt = Instant.parse("2026-08-01T23:03:00Z").minusSeconds(i.toLong()),
                rawMessage = "raw $i",
                reasonCode = "ONT_LOS",
                targetId = 4L
            )
        }
        every { oltLogEventRepository.findTop50ByTargetIdOrderByReceivedAtDesc(4L) } returns logs
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { onuRepository.findByOlt_IdAndBoardAndPortWithStatus(1L, 0, 0) } returns emptyList()
        every {
            onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(1L, 0, 0, 16)
        } returns Optional.empty()

        val context = builder.build(ponIncident())

        assertEquals(20, context!!.recentOltLogs.size)
    }
}
