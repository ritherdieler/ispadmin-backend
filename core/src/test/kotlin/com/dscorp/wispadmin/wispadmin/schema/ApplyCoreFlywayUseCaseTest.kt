package com.dscorp.wispadmin.wispadmin.schema

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class ApplyCoreFlywayUseCaseTest {

    private val migrator = mockk<CoreFlywayMigrator>()
    private val dataSource = mockk<DataSource>()
    private val flyway = mockk<Flyway>()
    private val useCase = ApplyCoreFlywayUseCase(migrator)

    @Test
    fun `migrates core classpath with baseline 38`() {
        every {
            migrator.migrate(
                dataSource,
                ApplyCoreFlywayUseCase.DEFAULT_LOCATIONS,
                true,
                ApplyCoreFlywayUseCase.DEFAULT_BASELINE,
            )
        } returns flyway

        val result = useCase(
            dataSource = dataSource,
            locations = ApplyCoreFlywayUseCase.DEFAULT_LOCATIONS,
            enabled = true,
            baselineVersion = ApplyCoreFlywayUseCase.DEFAULT_BASELINE,
        )

        assertTrue(result.isSuccess)
        assertEquals(flyway, result.getOrThrow())
        verify(exactly = 1) {
            migrator.migrate(dataSource, "classpath:db/migration", true, "38")
        }
    }

    @Test
    fun `wraps migrator failure in Result`() {
        every {
            migrator.migrate(any(), any(), any(), any())
        } throws IllegalStateException("priority column")

        val result = useCase(
            dataSource = dataSource,
            enabled = true,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("priority column", result.exceptionOrNull()?.message)
    }

    @Test
    fun `delegates skip when flyway is disabled`() {
        every {
            migrator.migrate(dataSource, "classpath:db/migration", false, "38")
        } returns null

        val result = useCase(dataSource = dataSource, enabled = false)

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrThrow())
        verify(exactly = 1) {
            migrator.migrate(dataSource, "classpath:db/migration", false, "38")
        }
    }
}
