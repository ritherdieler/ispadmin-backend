package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AcsProfileOwnershipTest {

    private val root = Path.of(System.getProperty("user.dir"))

    @Test
    fun acsRegistryDoesNotReadCoreCatalog() {
        val registry = Files.readString(
            root.resolve("acs/src/main/kotlin/com/dscorp/wispadmin/acs/genieacs/Tr069ModelProfileRegistry.kt"),
        )
        assertFalse(registry.contains("acs.profiles.catalog"), registry)
        assertFalse(registry.contains("ispadmin_staging"), registry)
        assertFalse(registry.contains("\$catalog"), registry)
        assertFalse(registry.contains("ACS_PROFILES_CATALOG"), registry)
        assertTrue(registry.contains("FROM tr069_model_profile"), registry)
    }

    @Test
    fun coreDoesNotOwnTr069ModelProfileTable() {
        val entityRoot = root.resolve("core/src/main/kotlin/com/dscorp/wispadmin/wispadmin")
        val hits = Files.walk(entityRoot).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }
                .filter { Files.readString(it).contains("@Table(name = \"tr069_model_profile\")") }
                .map { it.toString() }
                .toList()
        }
        assertTrue(hits.isEmpty(), hits.joinToString("\n"))
    }

    @Test
    fun acsFlywayOwnsProfileTable() {
        val sql = Files.readString(root.resolve("acs/src/main/resources/db/acs/V1__tr069_model_profile.sql"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS tr069_model_profile") || sql.contains("CREATE TABLE tr069_model_profile"), sql)
        assertTrue(sql.contains("client_wan_ip_connection_path"), sql)
        val acs = Files.readString(root.resolve("app/src/main/resources/application-acs.properties"))
        assertTrue(
            Regex("""^spring\.flyway\.enabled=true\s*$""", RegexOption.MULTILINE).containsMatchIn(acs),
            acs,
        )
        assertTrue(acs.contains("classpath:db/acs"), acs)
        assertFalse(acs.contains("acs.profiles.catalog"), acs)
    }

    @Test
    fun coreMigrationsAfterTheMoveNeverTouchTheProfileTable() {
        val offenders = Files.list(root.resolve("core/src/main/resources/db/migration")).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".sql") }
                .filter { versionOf(it.fileName.toString()) > 50 }
                .filter { Files.readString(it).contains("tr069_model_profile") }
                .map { it.fileName.toString() }
                .toList()
        }
        assertTrue(
            offenders.isEmpty(),
            "tr069_model_profile belongs to the ACS schema and V50 dropped it from the core; " +
                "these core migrations would fail on a clean staging: $offenders",
        )
    }

    @Test
    fun acsFlywayOwnsThePppoeWanPath() {
        val acsMigrations = Files.list(root.resolve("acs/src/main/resources/db/acs")).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".sql") }
                .map { Files.readString(it) }
                .toList()
        }
        assertTrue(
            acsMigrations.any { it.contains("client_wan_ppp_connection_path") },
            "the PPPoE WAN path column must be added by an ACS migration, not by the core",
        )
    }

    @Test
    fun wifiLastStateMigrationToleratesAnAlreadyPatchedSchema() {
        val sql = Files.readString(root.resolve("acs/src/main/resources/db/acs/V2__cpe_wifi_last_state.sql"))
        assertTrue(
            sql.contains("information_schema.COLUMNS"),
            "prestaging_acs already has the WiFi last-state columns, so V2 must check before adding them: $sql",
        )
        assertTrue(sql.contains("wifi_snapshot_json"), sql)
        assertTrue(sql.contains("wifi_associated_2g"), sql)
        assertTrue(sql.contains("wifi_associated_5g"), sql)
        assertTrue(sql.contains("wifi_associated_total"), sql)
        assertTrue(sql.contains("wifi_observed_at"), sql)
        assertTrue(sql.contains("wifi_quality_status"), sql)
        assertTrue(
            sql.contains("information_schema.TABLES"),
            "empty stg_acs has no cpe_record yet; V2 must no-op until Hibernate creates the table: $sql",
        )
        assertFalse(sql.contains("ADD COLUMN wifi_snapshot_json TEXT NULL,"), sql)
    }

    @Test
    fun thePppoeWanPathMigrationToleratesAnAlreadyPatchedSchema() {
        val sql = Files.readString(root.resolve("acs/src/main/resources/db/acs/V3__tr069_client_wan_ppp_path.sql"))
        assertTrue(
            sql.contains("information_schema.COLUMNS"),
            "stg_acs already got the column by hand, so the migration must check before adding it: $sql",
        )
        assertTrue(sql.contains("client_wan_ppp_connection_path"), sql)
    }

    @Test
    fun acsFlywayOwnsKnownPppoeWanPaths() {
        val sql = Files.readString(root.resolve("acs/src/main/resources/db/acs/V4__tr069_client_wan_ppp_paths.sql"))
        assertTrue(sql.contains("V2804AX15T"), sql)
        assertTrue(
            sql.contains("InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1"),
            sql,
        )
        assertTrue(sql.contains("F6600R"), sql)
        assertTrue(
            sql.contains("InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2"),
            sql,
        )
        assertFalse(sql.contains("stg_acs."), sql)
        assertFalse(sql.contains("ispadmin"), sql)
    }

    @Test
    fun stagingOneShotSqlPopulatesPppoeWanPathsWithoutTouchingCore() {
        val sql = Files.readString(root.resolve("scripts/sql/stg-acs-pppoe-wan-paths.sql"))
        assertTrue(sql.contains("stg_acs.tr069_model_profile"), sql)
        assertTrue(sql.contains("V2804AX15T"), sql)
        assertTrue(
            sql.contains("InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1"),
            sql,
        )
        assertTrue(sql.contains("F6600R"), sql)
        assertTrue(
            sql.contains("InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2"),
            sql,
        )
        assertFalse(sql.contains("ispadmin."), sql)
        assertFalse(sql.contains("ispadmin_staging"), sql)
    }

    private fun versionOf(fileName: String): Int =
        fileName.removePrefix("V").substringBefore("__").toIntOrNull() ?: 0

    @Test
    fun coreFlywayDropsMovedProfileTable() {
        val sql = Files.readString(root.resolve("core/src/main/resources/db/migration/V50__drop_tr069_model_profile.sql"))
        assertTrue(sql.contains("DROP TABLE IF EXISTS tr069_model_profile"), sql)
        val copy = Files.readString(root.resolve("scripts/sql/copy-tr069-profiles-to-acs.sql"))
        assertTrue(copy.contains("INSERT INTO prod_acs.tr069_model_profile"), copy)
        assertTrue(copy.contains("INSERT INTO stg_acs.tr069_model_profile"), copy)
        assertTrue(copy.contains("FROM ispadmin.tr069_model_profile"), copy)
    }
}
