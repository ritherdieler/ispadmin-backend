package com.dscorp.wispadmin.wispadmin.adapter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

class IdentityChangePublisherTest {

    @Test
    fun publisherIsSkippedWhenServiceHealthSubsystemIsOff() {
        val condition = IdentityChangePublisher::class.java.getAnnotation(ConditionalOnProperty::class.java)
        assertNotNull(condition)
        assertEquals("gigafiber.subsystems.servicehealth", condition.prefix)
        assertEquals("enabled", condition.name.single())
        assertEquals("true", condition.havingValue)
    }
}
