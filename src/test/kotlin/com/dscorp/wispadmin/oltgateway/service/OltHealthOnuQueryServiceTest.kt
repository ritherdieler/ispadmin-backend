package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Optional

class OltHealthOnuQueryServiceTest {

    private val onus = mockk<OltMgrOnuRepository>()
    private val olts = mockk<OltMgrOltRepository>()
    private val service = OltHealthOnuQueryService(onus, olts)
    private val olt = OltMgrOlt(id = 4L, name = "olt-a")
    private val onu = OltMgrOnu(id = 11L, sn = "HWTC1", externalId = "ext-1", olt = olt, board = 0, port = 2, onuIndex = 8)

    @Test
    fun `findBySn maps inventory to a flat ref`() {
        every { onus.findBySnIgnoreCaseAndDeletedAtIsNull("HWTC1") } returns Optional.of(onu)

        val ref = service.findBySn("HWTC1")

        assertEquals(11L, ref?.id)
        assertEquals("HWTC1", ref?.sn)
        assertEquals("ext-1", ref?.externalId)
        assertEquals(4L, ref?.oltId)
        assertEquals("olt-a", ref?.oltName)
        assertEquals(2, ref?.port)
    }

    @Test
    fun `findBySn resolves Genie serial to VSOL inventory via hex suffix`() {
        val vsol = OltMgrOnu(id = 7627L, sn = "VSOL0031C0B6", externalId = "ext-vsol", olt = olt, board = 1, port = 6, onuIndex = 10)
        every { onus.findBySnIgnoreCaseAndDeletedAtIsNull("12345B4641531C0B6") } returns Optional.empty()
        every { onus.findBySnIgnoreCaseAndDeletedAtIsNull("12345B4641531C0B6".uppercase()) } returns Optional.empty()
        every { onus.findBySnSuffixIgnoreCaseAndDeletedAtIsNull("31C0B6") } returns listOf(vsol)

        val ref = service.findBySn("12345B4641531C0B6")

        assertEquals(7627L, ref?.id)
        assertEquals("VSOL0031C0B6", ref?.sn)
        assertEquals(1, ref?.board)
        assertEquals(6, ref?.port)
    }

    @Test
    fun `findBySn suffix ambiguity degrades to null`() {
        every { onus.findBySnIgnoreCaseAndDeletedAtIsNull("12345B4641531C0B6") } returns Optional.empty()
        every { onus.findBySnSuffixIgnoreCaseAndDeletedAtIsNull("31C0B6") } returns listOf(onu, onu)

        assertNull(service.findBySn("12345B4641531C0B6"))
    }

    @Test
    fun `missing onu degrades to null`() {
        every { onus.findByExternalIdAndDeletedAtIsNull("missing") } returns Optional.empty()
        assertNull(service.findByExternalId("missing"))
    }

    @Test
    fun `findOltIdByName uses olt inventory`() {
        every { olts.findByName("olt-a") } returns Optional.of(olt)
        assertEquals(4L, service.findOltIdByName("olt-a"))
    }
}
