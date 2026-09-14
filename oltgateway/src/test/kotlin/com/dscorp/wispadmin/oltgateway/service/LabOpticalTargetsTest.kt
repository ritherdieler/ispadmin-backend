package com.dscorp.wispadmin.oltgateway.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LabOpticalTargetsTest {
    @Test
    fun `empty lab list yields no targets`() {
        assertEquals(emptyList<LabOpticalTargets.Target>(), LabOpticalTargets.resolve(emptyList()) { null })
    }

    @Test
    fun `resolves every lab onu and skips unmapped`() {
        val a = LabOnuRef(1L, "VSOL0031C0B6", 2L, 1, 6, 10)
        val b = LabOnuRef(2L, "VSOL00ABCDEF", 2L, 1, 7, 3)
        val targets = LabOpticalTargets.resolve(listOf(2329, 2330, 2331)) { id ->
            when (id) {
                2329 -> a
                2330 -> b
                else -> null
            }
        }
        assertEquals(listOf(2329, 2330), targets.map { it.subscriptionId })
        assertEquals(listOf(6, 7), targets.map { it.onu.port })
    }
}
