package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.WispAdminApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
