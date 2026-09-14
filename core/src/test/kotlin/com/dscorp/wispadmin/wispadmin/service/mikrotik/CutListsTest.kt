package com.dscorp.wispadmin.wispadmin.service.mikrotik

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CutListsTest {

    @Test
    fun `debtors and cancelled are independent lists with independent drop rules`() {
        assertEquals("deudores", CutLists.DEBTORS.name)
        assertEquals("cancelados", CutLists.CANCELLED.name)
        assertNotEquals(CutLists.DEBTORS.dropComment, CutLists.CANCELLED.dropComment)
    }

    @Test
    fun `debtors keeps the legacy drop comment already deployed on MK2`() {
        assertEquals(DebtorCutRulePlacement.DROP_COMMENT, CutLists.DEBTORS.dropComment)
    }

    @Test
    fun `all contains both lists`() {
        assertEquals(listOf(CutLists.DEBTORS, CutLists.CANCELLED), CutLists.ALL)
        assertTrue(CutLists.ALL.map { it.name }.toSet().size == 2)
    }
}
