package com.dscorp.wispadmin.wispadmin.schema

import org.flywaydb.core.Flyway
import javax.sql.DataSource

class ApplyCoreFlywayUseCase(
    private val migrator: CoreFlywayMigrator,
) {
    operator fun invoke(
        dataSource: DataSource,
        locations: String = DEFAULT_LOCATIONS,
        enabled: Boolean,
        baselineVersion: String = DEFAULT_BASELINE,
    ): Result<Flyway?> = runCatching {
        migrator.migrate(dataSource, locations, enabled, baselineVersion)
    }

    companion object {
        const val DEFAULT_LOCATIONS = "classpath:db/migration"
        const val DEFAULT_BASELINE = "38"
    }
}
