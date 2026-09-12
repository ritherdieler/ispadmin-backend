package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SubscriptionPppoeMigrationTest {

    private val root = Path.of(System.getProperty("user.dir"))

    private val sql: String by lazy {
        Files.readString(
            root.resolve("src/main/resources/db/migration/V52__subscription_pppoe.sql")
        )
    }

    @Test
    fun legacyTempTableDeclaresItsOwnCollation() {
        val createTemp = sql.substringAfter("CREATE TEMPORARY TABLE").substringBefore(";")
        assertTrue(
            createTemp.contains("COLLATE=utf8mb4_0900_ai_ci", ignoreCase = true),
            "temp table must pin its collation; the JDBC connection collation is not the schema one " +
                "and an implicit mismatch raises MySQL error 1267"
        )
    }

    @Test
    fun legacyJoinCollatesBothSidesExplicitly() {
        val join = sql.substringAfter("JOIN `tmp_pppoe_legacy`").substringBefore("SET")
        val collateHits = Regex("COLLATE\\s+utf8mb4_0900_ai_ci", RegexOption.IGNORE_CASE)
            .findAll(join)
            .count()
        assertTrue(
            collateHits >= 2,
            "both sides of the remote_address = ip comparison must be collated explicitly, found $collateHits"
        )
    }

    @Test
    fun migrationIsRerunnableAfterAPartialFailure() {
        assertTrue(
            sql.contains("DROP TEMPORARY TABLE IF EXISTS `tmp_pppoe_legacy`"),
            "a failed run leaves the temp table behind only within its session, but the guard keeps " +
                "the script rerunnable when Flyway retries"
        )
    }
}
