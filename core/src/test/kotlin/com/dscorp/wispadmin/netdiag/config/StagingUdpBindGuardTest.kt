package com.dscorp.wispadmin.netdiag.config

import com.dscorp.wispadmin.netdiag.service.NetDiagSnmpTrapUdpListener
import com.dscorp.wispadmin.netdiag.service.NetDiagSyslogUdpListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Profile

class StagingUdpBindGuardTest {

    @Test
    fun udp_listeners_do_not_load_on_staging_profile() {
        val netdiagTrap = NetDiagSnmpTrapUdpListener::class.java.getAnnotation(Profile::class.java)
        val syslog = NetDiagSyslogUdpListener::class.java.getAnnotation(Profile::class.java)
        assertNotNull(netdiagTrap)
        assertEquals("!staging", netdiagTrap!!.value[0])
        assertNotNull(syslog)
        assertEquals("!staging", syslog!!.value[0])
    }
}
