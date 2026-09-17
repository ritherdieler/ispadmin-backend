package com.dscorp.wispadmin.oltgateway.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnuSerialMatcherTest {

    @Test
    fun `matches vendor compact against autofind hex plus hyphenated alias`() {
        assertTrue(
            OnuSerialMatcher.matches(
                wanted = "VSOL00323AF6",
                displayed = "56534F4C00323AF6 (VSOL-00323AF6)",
            )
        )
    }

    @Test
    fun `matches hex CLI form against vendor compact`() {
        assertTrue(OnuSerialMatcher.matches("56534F4C00323AF6", "VSOL00323AF6"))
    }

    @Test
    fun `matches last 6 hex suffix inside longer ACS serial`() {
        assertTrue(OnuSerialMatcher.matches("VSOL00323AF6", "12345B46415323AF6"))
    }

    @Test
    fun `rejects a different serial`() {
        assertFalse(OnuSerialMatcher.matches("VSOL00323AF6", "56534F4C0031C0B6 (VSOL-0031C0B6)"))
    }
}
