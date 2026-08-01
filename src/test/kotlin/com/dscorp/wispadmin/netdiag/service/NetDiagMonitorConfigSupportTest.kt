package com.dscorp.wispadmin.netdiag.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetDiagMonitorConfigSupportTest {

    @Test
    fun `kind por defecto es mikrotik cuando falta`() {
        assertEquals("mikrotik", NetDiagMonitorConfigSupport.kind(null))
        assertEquals("mikrotik", NetDiagMonitorConfigSupport.kind("{}"))
        assertEquals("mikrotik", NetDiagMonitorConfigSupport.kind("""{"criticalInterfaces":["ether1"]}"""))
    }

    @Test
    fun `lee kind olt y pon`() {
        assertEquals("olt", NetDiagMonitorConfigSupport.kind("""{"kind":"olt","oltId":"x"}"""))
        assertEquals("pon", NetDiagMonitorConfigSupport.kind("""{"kind":"pon","board":0,"port":1}"""))
    }

    @Test
    fun `isMikrotikPollable solo mikrotik`() {
        assertTrue(NetDiagMonitorConfigSupport.isMikrotikPollable("""{"kind":"mikrotik"}"""))
        assertTrue(NetDiagMonitorConfigSupport.isMikrotikPollable(null))
        assertFalse(NetDiagMonitorConfigSupport.isMikrotikPollable("""{"kind":"olt"}"""))
        assertFalse(NetDiagMonitorConfigSupport.isMikrotikPollable("""{"kind":"pon"}"""))
    }
}
