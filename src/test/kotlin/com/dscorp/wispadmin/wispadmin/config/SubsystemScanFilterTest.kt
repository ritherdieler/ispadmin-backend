package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.servicehealth.controller.ServiceHealthController
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory
import org.springframework.mock.env.MockEnvironment

class SubsystemScanFilterTest {

    private val factory = SimpleMetadataReaderFactory()

    private fun filter(enabled: Boolean): SubsystemScanFilter {
        val environment = MockEnvironment()
        environment.setProperty("gigafiber.subsystems.servicehealth.enabled", enabled.toString())
        val filter = SubsystemScanFilter()
        filter.setEnvironment(environment)
        return filter
    }

    @Test
    fun excludesServiceHealthWhenDisabled() {
        val reader = factory.getMetadataReader(ServiceHealthController::class.java.name)
        assertTrue(filter(false).match(reader, factory))
    }

    @Test
    fun keepsServiceHealthWhenEnabled() {
        val reader = factory.getMetadataReader(ServiceHealthController::class.java.name)
        assertFalse(filter(true).match(reader, factory))
    }

    @Test
    fun neverExcludesCorePackage() {
        val reader = factory.getMetadataReader(GigafiberEnvironmentProperties::class.java.name)
        val environment = StandardEnvironment()
        val filter = SubsystemScanFilter()
        filter.setEnvironment(environment)
        assertFalse(filter.match(reader, factory))
    }

    @Test
    fun entityPackagesOmitDisabledSubsystems() {
        val environment = MockEnvironment()
        environment.setProperty("gigafiber.subsystems.servicehealth.enabled", "false")
        environment.setProperty("gigafiber.subsystems.traffic.enabled", "true")
        val packages = SubsystemScanFilter.entityPackages(environment)
        assertTrue(packages.contains("com.dscorp.wispadmin.wispadmin"))
        assertTrue(packages.contains("com.dscorp.wispadmin.traffic"))
        assertFalse(packages.contains("com.dscorp.wispadmin.servicehealth"))
    }
}
