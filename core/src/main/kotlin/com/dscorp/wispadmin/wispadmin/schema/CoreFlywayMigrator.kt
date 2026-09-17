package com.dscorp.wispadmin.wispadmin.schema

import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun interface CoreFlywayMigrator {
    fun migrate(
        dataSource: DataSource,
        locations: String,
        enabled: Boolean,
        baselineVersion: String,
    ): Flyway?
}
