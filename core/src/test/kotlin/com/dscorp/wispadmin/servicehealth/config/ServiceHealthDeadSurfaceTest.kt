package com.dscorp.wispadmin.servicehealth.config

import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

class ServiceHealthDeadSurfaceTest {

    @Test
    fun `collection leftovers are gone`() {
        val names = ServiceHealthProperties::class.memberProperties.map { it.name }.toSet()
        assertFalse("sharedIncidentNotificationsEnabled" in names)
        assertFalse("labPeriodicInformSeconds" in names)
        assertFalse("opticalPullEnabled" in names)
        assertFalse(AcsSubscriptionPort::class.members.any { it.name == "labSubscriptionIds" })
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.servicehealth.service.HealthOltOpticalPullService")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.servicehealth.client.HealthStateObservation")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.servicehealth.service.HealthSqlTime")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.servicehealth.domain.ReadCapabilityProfile")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.servicehealth.repository.ReadCapabilityProfileRepository")
        }
    }
}
