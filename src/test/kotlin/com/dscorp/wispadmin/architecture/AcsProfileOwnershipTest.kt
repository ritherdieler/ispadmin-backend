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
            root.resolve("src/main/kotlin/com/dscorp/wispadmin/acs/genieacs/Tr069ModelProfileRegistry.kt"),
        )
        assertFalse(registry.contains("acs.profiles.catalog"), registry)
        assertFalse(registry.contains("ispadmin_staging"), registry)
        assertFalse(registry.contains("\$catalog"), registry)
        assertFalse(registry.contains("ACS_PROFILES_CATALOG"), registry)
        assertTrue(registry.contains("FROM tr069_model_profile"), registry)
    }

    @Test
    fun coreDoesNotOwnTr069ModelProfileTable() {
        val entityRoot = root.resolve("src/main/kotlin/com/dscorp/wispadmin/wispadmin")
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
        val sql = Files.readString(root.resolve("src/main/resources/db/acs/V1__tr069_model_profile.sql"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS tr069_model_profile") || sql.contains("CREATE TABLE tr069_model_profile"), sql)
        assertTrue(sql.contains("client_wan_ip_connection_path"), sql)
        val acs = Files.readString(root.resolve("src/main/resources/application-acs.properties"))
        assertTrue(
            Regex("""^spring\.flyway\.enabled=true\s*$""", RegexOption.MULTILINE).containsMatchIn(acs),
            acs,
        )
        assertTrue(acs.contains("classpath:db/acs"), acs)
        assertFalse(acs.contains("acs.profiles.catalog"), acs)
    }

    @Test
    fun coreFlywayDropsMovedProfileTable() {
        val sql = Files.readString(root.resolve("src/main/resources/db/migration/V50__drop_tr069_model_profile.sql"))
        assertTrue(sql.contains("DROP TABLE IF EXISTS tr069_model_profile"), sql)
        val copy = Files.readString(root.resolve("scripts/sql/copy-tr069-profiles-to-acs.sql"))
        assertTrue(copy.contains("INSERT INTO prod_acs.tr069_model_profile"), copy)
        assertTrue(copy.contains("INSERT INTO stg_acs.tr069_model_profile"), copy)
        assertTrue(copy.contains("FROM ispadmin.tr069_model_profile"), copy)
    }
}
