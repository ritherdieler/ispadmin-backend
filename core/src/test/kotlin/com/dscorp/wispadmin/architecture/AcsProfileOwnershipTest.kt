package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class AcsProfileOwnershipTest {

    private val root: Path = generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }

    private val forbiddenSources = listOf(
        "acs/src/main/kotlin/com/dscorp/wispadmin/acs/controller/AcsProfileController.kt",
        "acs/src/main/kotlin/com/dscorp/wispadmin/acs/entity/Tr069ModelProfileRecord.kt",
        "acs/src/main/kotlin/com/dscorp/wispadmin/acs/genieacs/GenieAcsCsvProfileExtractor.kt",
        "acs/src/main/kotlin/com/dscorp/wispadmin/acs/genieacs/Tr069ModelProfile.kt",
        "acs/src/main/kotlin/com/dscorp/wispadmin/acs/genieacs/Tr069ModelProfileImportService.kt",
        "acs/src/main/kotlin/com/dscorp/wispadmin/acs/genieacs/Tr069ModelProfileRegistry.kt",
        "acs/src/main/kotlin/com/dscorp/wispadmin/acs/genieacs/Tr069ProvisioningService.kt",
        "acs/src/main/kotlin/com/dscorp/wispadmin/acs/genieacs/Tr069WifiSecurityPrepSpec.kt",
        "acs/src/main/kotlin/com/dscorp/wispadmin/acs/repository/Tr069ModelProfileRepository.kt",
        "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/Tr069ModelProfileController.kt",
        "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/genieacs/GenieAcsCsvProfileExtractor.kt",
        "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/genieacs/Tr069AsyncApplicator.kt",
        "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/genieacs/Tr069ModelProfile.kt",
        "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/genieacs/Tr069ModelProfileRegistry.kt",
        "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/genieacs/Tr069PostInstallProvisioner.kt",
        "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/genieacs/Tr069ProvisioningService.kt",
        "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/genieacs/Tr069WifiSecurityPrepSpec.kt",
        "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/config/Tr069AsyncConfig.kt",
        "oltgateway/src/main/kotlin/com/dscorp/wispadmin/oltgateway/controller/AcsProfileProxyController.kt",
        "scripts/sql/copy-tr069-profiles-to-acs.sql",
        "scripts/sql/stg-acs-pppoe-wan-paths.sql",
    )

    private val forbiddenTokens = listOf(
        "/api/acs/v1/profiles",
        "/admin/tr069-profiles",
        "/api/olt-gateway/acs/profiles",
        "GenieAcsCsvProfileExtractor",
        "Tr069ModelProfileImportService",
        "class Tr069ProvisioningService",
        "Tr069ModelProfileRegistry",
        "Tr069ModelProfileController",
        "AcsProfileController",
        "AcsProfileProxyController",
    )

    @Test
    fun csvProfileMechanismIsGone() {
        val leftovers = forbiddenSources.filter { Files.exists(root.resolve(it)) }
        assertTrue(leftovers.isEmpty(), leftovers.joinToString("\n"))
    }

    @Test
    fun productionKotlinDoesNotExposeCsvProfileEndpoints() {
        val modules = listOf("acs", "core", "oltgateway")
        val hits = modules.flatMap { module ->
            val start = root.resolve("$module/src/main/kotlin")
            if (!Files.isDirectory(start)) return@flatMap emptyList()
            Files.walk(start).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                    .flatMap { file ->
                        Files.readAllLines(file).asSequence().mapIndexedNotNull { index, line ->
                            val token = forbiddenTokens.firstOrNull { line.contains(it) } ?: return@mapIndexedNotNull null
                            "${file.relativeTo(root)}:${index + 1}: $token"
                        }
                    }
                    .toList()
            }
        }
        assertTrue(hits.isEmpty(), hits.joinToString("\n"))
    }

    @Test
    fun acsFlywayDropsAbandonedProfileTable() {
        val sql = Files.readString(root.resolve("acs/src/main/resources/db/acs/V5__drop_tr069_model_profile.sql"))
        assertTrue(sql.contains("DROP TABLE IF EXISTS tr069_model_profile"), sql)
    }

    @Test
    fun coreFlywayStillDropsMovedProfileTable() {
        val sql = Files.readString(root.resolve("core/src/main/resources/db/migration/V50__drop_tr069_model_profile.sql"))
        assertTrue(sql.contains("DROP TABLE IF EXISTS tr069_model_profile"), sql)
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
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun catalogSqlDoesNotSeedCsvProfiles() {
        val sql = Files.readString(root.resolve("scripts/sql/staging-e2e-registration-catalog.sql"))
        assertFalse(sql.contains("tr069_model_profile"), sql)
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

    private fun versionOf(fileName: String): Int =
        fileName.removePrefix("V").substringBefore("__").toIntOrNull() ?: 0
}
