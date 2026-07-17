package com.dscorp.wispadmin.oltgateway.service.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GponBoardClassifierTest {

    @Test
    fun `accepts normal gpfd boards`() {
        assertTrue(GponBoardClassifier.isGponBoard("H805GPFD", "Normal"))
        assertTrue(GponBoardClassifier.isGponBoard("H806GPFD", "Normal"))
    }

    @Test
    fun `rejects control and empty boards`() {
        assertFalse(GponBoardClassifier.isGponBoard("H801MCUD1", "Normal"))
        assertFalse(GponBoardClassifier.isGponBoard("", "Normal"))
        assertFalse(GponBoardClassifier.isGponBoard("H805GPFD", "Failed"))
    }

    @Test
    fun `default ports for known boards`() {
        assertEquals(16, GponBoardClassifier.defaultPortCount("H805GPFD", 16))
        assertEquals(8, GponBoardClassifier.defaultPortCount("H802GPBD", 16))
        assertEquals(16, GponBoardClassifier.defaultPortCount("UNKNOWN", 16))
    }
}
