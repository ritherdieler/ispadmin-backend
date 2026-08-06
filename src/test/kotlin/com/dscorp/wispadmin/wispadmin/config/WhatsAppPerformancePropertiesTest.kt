package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Properties

class WhatsAppPerformancePropertiesTest {

    private fun loadProperties(resourceName: String): Properties {
        val properties = Properties()
        javaClass.classLoader.getResourceAsStream(resourceName)!!.use { properties.load(it) }
        return properties
    }

    private fun assertTuning(properties: Properties) {
        assertEquals("50", properties.getProperty("spring.jpa.properties.hibernate.jdbc.batch_size"))
        assertEquals("true", properties.getProperty("spring.jpa.properties.hibernate.order_inserts"))
        assertEquals("true", properties.getProperty("spring.jpa.properties.hibernate.order_updates"))
        assertEquals("16", properties.getProperty("spring.jpa.properties.hibernate.default_batch_fetch_size"))
        assertEquals("20", properties.getProperty("spring.datasource.hikari.maximum-pool-size"))
        assertEquals("5", properties.getProperty("spring.datasource.hikari.minimum-idle"))
    }

    @Test
    fun `application-prod define batch de hibernate y pool de hikari`() {
        assertTuning(loadProperties("application-prod.properties"))
    }

    @Test
    fun `application-dev define batch de hibernate y pool de hikari`() {
        assertTuning(loadProperties("application-dev.properties"))
    }
}
