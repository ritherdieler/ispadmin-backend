package com.dscorp.wispadmin.shared.persistence

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SatelliteJpaFlywayRepairTest {

    @Test
    fun migrateIfEnabledRepairsFailedMigrationsBeforeMigrate() {
        val src = Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("shared/src/main/kotlin/com/dscorp/wispadmin/shared/persistence/SatelliteJpa.kt"),
        )
        val repairAt = src.indexOf("flyway.repair()")
        val migrateAt = src.indexOf("flyway.migrate()")
        assertTrue(repairAt >= 0, src)
        assertTrue(migrateAt > repairAt, src)
    }

    @Test
    fun entityManagerFactoryAppliesSpringBootNamingStrategies() {
        val src = Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("shared/src/main/kotlin/com/dscorp/wispadmin/shared/persistence/SatelliteJpa.kt"),
        )
        assertTrue(src.contains("SpringPhysicalNamingStrategy"), src)
        assertTrue(src.contains("SpringImplicitNamingStrategy"), src)
    }
}
