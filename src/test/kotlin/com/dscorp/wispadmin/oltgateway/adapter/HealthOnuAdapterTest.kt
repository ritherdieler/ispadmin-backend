package com.dscorp.wispadmin.oltgateway.adapter

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

class HealthOnuAdapterTest {

    private val onus = mockk<OltMgrOnuRepository>()
    private val olts = mockk<OltMgrOltRepository>()
    private val adapter = HealthOnuAdapter(onus, olts)
    private val olt = OltMgrOlt(id = 4L, name = "olt-a")
    private val onu = OltMgrOnu(id = 11L, sn = "HWTC1", externalId = "ext-1", olt = olt, board = 0, port = 2, onuIndex = 8)

    @Test
    fun `findBySn maps inventory to a flat ref`() {
        every { onus.findBySnIgnoreCaseAndDeletedAtIsNull("HWTC1") } returns Optional.of(onu)

        val ref = adapter.findBySn("HWTC1")

        assertEquals(11L, ref?.id)
        assertEquals("HWTC1", ref?.sn)
        assertEquals("ext-1", ref?.externalId)
        assertEquals(4L, ref?.oltId)
        assertEquals("olt-a", ref?.oltName)
        assertEquals(2, ref?.port)
    }

    @Test
    fun `missing onu degrades to null`() {
        every { onus.findByExternalIdAndDeletedAtIsNull("missing") } returns Optional.empty()
        assertNull(adapter.findByExternalId("missing"))
    }

    @Test
    fun `findOltIdByName uses olt inventory`() {
        every { olts.findByName("olt-a") } returns Optional.of(olt)
        assertEquals(4L, adapter.findOltIdByName("olt-a"))
    }
}
