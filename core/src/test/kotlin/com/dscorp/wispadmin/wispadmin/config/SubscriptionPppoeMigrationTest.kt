package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SubscriptionPppoeMigrationTest {

    private val root = repoRoot()

    private val flywayDir: Path
        get() = root.resolve("core/src/main/resources/db/migration")

    private val v52: String by lazy {
        Files.readString(flywayDir.resolve("V52__subscription_pppoe.sql"))
    }

    private val coreFlywaySql: List<Pair<String, String>> by lazy {
        Files.list(flywayDir).use { paths ->
            paths
                .filter { it.fileName.toString().startsWith("V") && it.fileName.toString().endsWith(".sql") }
                .sorted()
                .map { it.fileName.toString() to Files.readString(it) }
                .toList()
        }
    }

    @Test
    fun v52AddsAccessModeAndPppoeColumnsWithoutBackfill() {
        assertTrue(v52.contains("ADD COLUMN `access_mode`"), v52)
        assertTrue(v52.contains("STATIC_IP"), v52)
        assertTrue(v52.contains("information_schema.COLUMNS"), v52)
        assertTrue(v52.contains("ADD COLUMN `pppoe_username`"), v52)
        assertTrue(v52.contains("ADD COLUMN `pppoe_password_enc`"), v52)
        assertTrue(v52.contains("ADD COLUMN `pppoe_profile`"), v52)
        assertTrue(v52.contains("ADD COLUMN `pppoe_provision_status`"), v52)
        assertTrue(v52.contains("ADD COLUMN `pppoe_last_ip`"), v52)
        assertTrue(v52.contains("ADD COLUMN `management_ip`"), v52)
        assertTrue(v52.contains("ADD COLUMN `management_mac`"), v52)
        assertTrue(v52.contains("idx_subscription_access_mode"), v52)
    }

    @Test
    fun coreFlywayMustNotBlindlyBackfillPppoeFixed() {
        coreFlywaySql.forEach { (name, sql) ->
            assertFalse(
                Regex("""access_mode`\s*=\s*'PPPOE_FIXED'""", RegexOption.IGNORE_CASE).containsMatchIn(sql),
                "$name must not assign PPPOE_FIXED in Flyway; leave STATIC_IP and backfill out of band: $sql",
            )
            assertFalse(
                sql.contains("tmp_pppoe_legacy"),
                "$name must not ship the hardcoded PPPoE username dump: $sql",
            )
            val updatesSubscription = Regex(
                """UPDATE\s+`subscription`\s""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(sql)
            assertFalse(
                updatesSubscription && sql.contains("pppoe_username"),
                "$name must not UPDATE subscription.pppoe_username during migrate: $sql",
            )
        }
    }

    @Test
    fun uniquePppoeUsernameIsSafeOnlyWhileUsernamesStayNull() {
        val addsUnique = Regex(
            """UNIQUE\s+KEY\s+`uk_subscription_pppoe_username`""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(v52)
        assertTrue(
            addsUnique,
            "Hibernate validate expects uk_subscription_pppoe_username (entity unique=true)",
        )
        assertFalse(
            Regex("""UPDATE\s+`?subscription`?""", RegexOption.IGNORE_CASE).containsMatchIn(v52),
            "UNIQUE on pppoe_username plus any UPDATE is unsafe: prod has IP 192.168.26.199 twice",
        )
        assertFalse(v52.contains("192.168.26.199"), v52)
    }

    @Test
    fun optionalBackfillScriptGuardsTheDuplicateIpAndIsNotFlyway() {
        val script = root.resolve("scripts/sql/backfill-subscription-pppoe-legacy.sql")
        assertTrue(Files.exists(script), "optional backfill must live outside Flyway at $script")
        val sql = Files.readString(script)
        assertFalse(script.startsWith(flywayDir), script.toString())
        assertTrue(
            sql.contains("192.168.26.199"),
            "duplicate IP must be named so the guard cannot be dropped silently",
        )
        assertTrue(
            Regex("""SIGNAL\s+SQLSTATE""", RegexOption.IGNORE_CASE).containsMatchIn(sql) ||
                sql.contains("COUNT(*)") && sql.contains("HAVING"),
            "backfill must abort when 192.168.26.199 maps to more than one subscription",
        )
        assertTrue(
            sql.contains("Do not run on cutover") || sql.contains("do not run on cutover"),
            sql.take(400),
        )
        assertTrue(sql.contains("STATIC_IP"), sql.take(400))
    }

    private fun repoRoot(): Path {
        var dir = Path.of(System.getProperty("user.dir"))
        repeat(6) {
            if (Files.exists(dir.resolve("core/src/main/resources/db/migration/V52__subscription_pppoe.sql"))) {
                return dir
            }
            dir = dir.parent ?: return Path.of(System.getProperty("user.dir"))
        }
        return Path.of(System.getProperty("user.dir"))
    }
}
