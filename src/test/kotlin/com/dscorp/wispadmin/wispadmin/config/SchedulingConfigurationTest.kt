package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.WispAdminApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

class SchedulingConfigurationTest {

    @Test
    fun scheduling_is_opt_in_via_gigafiber_flag_and_not_on_the_application_class() {
        val conditional = SchedulingConfiguration::class.java.getAnnotation(ConditionalOnProperty::class.java)
        assertNotNull(conditional)
        assertEquals("gigafiber.scheduling.enabled", conditional!!.name[0])
        assertEquals("true", conditional.havingValue)
        assertTrue(conditional.matchIfMissing)
        assertNotNull(SchedulingConfiguration::class.java.getAnnotation(EnableScheduling::class.java))
        assertNull(WispAdminApplication::class.java.getAnnotation(EnableScheduling::class.java))
    }
}

class StagingUdpBindGuardTest {

    @Test
    fun udp_listeners_do_not_load_on_staging_profile() {
        val netdiagTrap = com.dscorp.wispadmin.netdiag.service.NetDiagSnmpTrapUdpListener::class.java
            .getAnnotation(Profile::class.java)
        val syslog = com.dscorp.wispadmin.netdiag.service.NetDiagSyslogUdpListener::class.java
            .getAnnotation(Profile::class.java)
        assertNotNull(netdiagTrap)
        assertEquals("!staging", netdiagTrap!!.value[0])
        assertNotNull(syslog)
        assertEquals("!staging", syslog!!.value[0])
    }
}
