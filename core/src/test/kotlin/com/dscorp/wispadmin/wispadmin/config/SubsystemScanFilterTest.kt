package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.servicehealth.controller.ServiceHealthController
import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
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
        assertFalse(packages.contains("com.dscorp.wispadmin.servicehealth"))
    }

    @Test
    fun primaryEntityPackagesExcludeSatelliteSchemas() {
        val environment = MockEnvironment()
        val packages = SubsystemScanFilter.entityPackages(environment).toList()
        assertTrue(packages.contains("com.dscorp.wispadmin.wispadmin"))
        assertTrue(packages.contains("com.dscorp.wispadmin.observability"))
        assertTrue(packages.contains("com.dscorp.wispadmin.netdiag"))
        assertTrue(packages.contains("com.dscorp.wispadmin.servicehealth"))
        assertFalse(packages.contains("com.dscorp.wispadmin.acs"))
        assertFalse(packages.contains("com.dscorp.wispadmin.oltgateway"))
        assertFalse(packages.contains("com.dscorp.wispadmin.traffic"))
    }

    @Test
    fun applicationScansServiceHealth() {
        val main = com.dscorp.wispadmin.wispadmin.WispAdminApplication::class.java
        assertTrue(main.getAnnotation(org.springframework.context.annotation.ComponentScan::class.java).basePackages.contains("com.dscorp.wispadmin.servicehealth"))
        val jpa = main.getAnnotation(org.springframework.data.jpa.repository.config.EnableJpaRepositories::class.java)
        assertTrue(jpa.basePackages.contains("com.dscorp.wispadmin.servicehealth"))
        assertTrue(
            jpa.excludeFilters.flatMap { it.classes.asList() }
                .any { it.toString().contains("SubsystemScanFilter") }
        )
        assertTrue(main.getAnnotation(SubsystemEntityScan::class.java) != null)
    }
}
