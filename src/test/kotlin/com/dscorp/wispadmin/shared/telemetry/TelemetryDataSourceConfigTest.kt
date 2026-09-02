package com.dscorp.wispadmin.shared.telemetry

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class TelemetryDataSourceConfigTest {

    private val primary = mockk<DataSource>(relaxed = true)
    private val config = TelemetryDataSourceConfig()

    @Test
    fun `sin URL propia reutiliza el datasource de negocio`() {
        val properties = TelemetryDataSourceProperties()

        assertFalse(TelemetryDataSourceHealth(properties, primary).usesDedicatedSchema())
    }

    @Test
    fun `con URL propia abre un pool aparte`() {
        val properties = TelemetryDataSourceProperties().apply {
            url = "jdbc:h2:mem:telemetry;MODE=MySQL;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
        }

        val telemetry = config.dedicatedTelemetryDataSource(properties)

        assertTrue(telemetry !== primary)
        assertTrue(TelemetryDataSourceHealth(properties, primary).usesDedicatedSchema())
        (telemetry as AutoCloseable).close()
    }
}
