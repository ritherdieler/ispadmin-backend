package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuAutofind
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuAutofindRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

class OltInventoryServiceTest {

    private val onus = mockk<OltMgrOnuRepository>()
    private val olts = mockk<OltMgrOltRepository>()
    private val autofind = mockk<OltMgrOnuAutofindRepository>()
    private val service = OltInventoryService(onus, olts, autofind)

    private val olt = OltMgrOlt(id = 4L, name = "olt-a")
    private val onu = OltMgrOnu(
        id = 11L,
        sn = "HWTC1",
        externalId = "ext-1",
        olt = olt,
        board = 0,
        port = 2,
        onuIndex = 8,
        onuTypeName = "HG8245H"
    ).also { entity ->
        entity.status = OltMgrOnuStatusCurrent(
            onu = entity,
            runState = "online",
            onuRxDbm = BigDecimal("-18.50"),
            temperatureC = 42,
            distanceM = 1200
        )
    }

    @Test
    fun `findBySn returns optical snapshot from current status`() {
        every { onus.findBySnIgnoreCaseAndDeletedAtIsNull("HWTC1") } returns Optional.of(onu)

        val snapshot = service.findBySn("HWTC1")

        assertEquals(11L, snapshot?.id)
        assertEquals("HWTC1", snapshot?.sn)
        assertEquals("online", snapshot?.runState)
        assertEquals(BigDecimal("-18.50"), snapshot?.onuRxDbm)
        assertEquals(42, snapshot?.temperatureC)
        assertEquals(1200, snapshot?.distanceM)
        assertEquals("HG8245H", snapshot?.onuTypeName)
    }

    @Test
    fun `findBySn resolves Genie serial via hex suffix`() {
        val vsol = OltMgrOnu(id = 7627L, sn = "VSOL0031C0B6", externalId = "ext-vsol", olt = olt, board = 1, port = 6, onuIndex = 10)
        every { onus.findBySnIgnoreCaseAndDeletedAtIsNull(any()) } returns Optional.empty()
        every { onus.findBySnSuffixIgnoreCaseAndDeletedAtIsNull("31C0B6") } returns listOf(vsol)

        val snapshot = service.findBySn("12345B4641531C0B6")

        assertEquals(7627L, snapshot?.id)
        assertEquals("VSOL0031C0B6", snapshot?.sn)
    }

    @Test
    fun `listAutofind maps cache rows`() {
        every { autofind.findAllByOrderByLastSeenAtDesc() } returns listOf(
            OltMgrOnuAutofind(sn = "SN1", frame = 0, board = 1, port = 2, ponType = "gpon", vendorId = "HWTC")
        )

        val rows = service.listAutofind()

        assertEquals(1, rows.size)
        assertEquals("SN1", rows[0].sn)
        assertEquals(1, rows[0].board)
        assertEquals("HWTC", rows[0].vendorId)
    }

    @Test
    fun `countConfigured uses active inventory rows`() {
        every { onus.countByDeletedAtIsNull() } returns 42L
        assertEquals(42L, service.countConfigured())
    }

    @Test
    fun `findByExternalId returns null when missing`() {
        every { onus.findByExternalIdAndDeletedAtIsNull("missing") } returns Optional.empty()
        assertNull(service.findByExternalId("missing"))
    }
}
