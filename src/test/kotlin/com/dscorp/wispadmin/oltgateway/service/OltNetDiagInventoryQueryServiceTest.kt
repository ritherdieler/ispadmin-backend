package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

class OltNetDiagInventoryQueryServiceTest {

    @Test
    fun `lista ONUs del PON`() {
        val oltRepository = mockk<OltMgrOltRepository>()
        val onuRepository = mockk<OltMgrOnuRepository>()
        val olt = OltMgrOlt(id = 1L, name = "gigafiber-ma5608t", ipAddress = "10.11.104.2")
        val onu = OltMgrOnu(id = 16L, sn = "HWTC1", olt = olt, board = 0, port = 1, onuIndex = 16)
        onu.status = OltMgrOnuStatusCurrent(
            onuId = 16L,
            onu = onu,
            runState = "offline",
            lastDownCause = "dying-gasp",
            onuRxDbm = BigDecimal("-21.10")
        )
        every { oltRepository.findByName("gigafiber-ma5608t") } returns Optional.of(olt)
        every { oltRepository.findByName("missing") } returns Optional.empty()
        every { onuRepository.findByOlt_IdAndBoardAndPortWithStatus(1L, 0, 1) } returns listOf(onu)
        every {
            onuRepository.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(1L, 0, 1, 16)
        } returns Optional.of(onu)

        val service = OltNetDiagInventoryQueryService(oltRepository, onuRepository)

        assertEquals(1L, service.findOltId("gigafiber-ma5608t"))
        val listed = service.listOnusOnPon("gigafiber-ma5608t", 0, 1)
        assertEquals(1, listed.size)
        assertEquals(16, listed[0].onuIndex)
        assertEquals("HWTC1", listed[0].sn)
        assertEquals("offline", listed[0].runState)
        assertEquals("dying-gasp", listed[0].lastDownCause)
        assertEquals(-21.10, listed[0].onuRxDbm)
        assertEquals("HWTC1", service.findOnu("gigafiber-ma5608t", 0, 1, 16)?.sn)
        assertNull(service.findOltId("missing"))
    }
}
