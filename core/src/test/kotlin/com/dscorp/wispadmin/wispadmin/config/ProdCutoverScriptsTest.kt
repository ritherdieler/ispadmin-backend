package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ProdCutoverScriptsTest {

    private val root = repoRoot()

    @Test
    fun prodSatelliteSchemaScriptCreatesEmptyProdSchemasOnly() {
        val sql = read("scripts/sql/create-prod-satellite-schemas.sql")
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS prod_acs"), sql)
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS prod_oltgateway"), sql)
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS prod_traffic"), sql)
        assertTrue(sql.contains("Do not run until cutover") || sql.contains("do not run until cutover"), sql.take(400))
        assertFalse(Regex("""DROP\s+DATABASE""", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
        assertFalse(sql.contains("ispadmin_staging"), sql)
        assertFalse(Regex("""INSERT\s+INTO""", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
    }

    @Test
    fun scratchSatelliteSchemaScriptNeverTargetsProdOrStaging() {
        val sql = read("scripts/sql/create-scratch-satellite-schemas.sql")
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS scratch_acs"), sql)
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS scratch_oltgateway"), sql)
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS scratch_traffic"), sql)
        assertFalse(sql.contains("CREATE DATABASE IF NOT EXISTS prod_"), sql)
        assertFalse(Regex("""CREATE DATABASE IF NOT EXISTS ispadmin\b""").containsMatchIn(sql), sql)
    }

    @Test
    fun flywayCloneScriptRefusesProdAndStagingSchemas() {
        val script = read("scripts/flyway-clone-ispadmin.sh")
        assertTrue(script.contains("ispadmin_flyway_clone"), script.take(800))
        assertTrue(script.contains("scratch_"), script)
        assertTrue(script.contains("ispadmin_staging"), script)
        assertTrue(
            script.contains("refuses") || script.contains("Refuse") || script.contains("forbidden"),
            script.take(800),
        )
        assertTrue(script.contains("V39") && script.contains("V55"), script)
        assertTrue(script.contains("baseline") || script.contains("baseline-version"), script)
        assertFalse(script.contains("deploy.sh --env prod"), script)
    }

    @Test
    fun flywayCloneHibernateValidateScriptRefusesLiveCatalogs() {
        val script = read("scripts/flyway-clone-hibernate-validate.sh")
        assertTrue(script.contains("ispadmin_flyway_clone"), script)
        assertTrue(script.contains("CORE_FLYWAY_CLONE_JDBC"), script)
        assertTrue(script.contains("Refuse") || script.contains("refuse"), script)
        assertTrue(script.contains("ispadmin_staging"), script)
        assertFalse(script.contains("deploy.sh --env prod"), script)
    }

    @Test
    fun prodTrafficDirectoryDryRunIsSelectOnly() {
        val sql = read("scripts/sql/prod-traffic-directory-dry-run.sql")
        assertTrue(sql.contains("SELECT only") || sql.contains("SELECT only."), sql.take(200))
        assertTrue(sql.contains("USE ispadmin") || sql.contains("ispadmin"), sql)
        assertFalse(Regex("""\b(UPDATE|DELETE|INSERT|DROP|ALTER)\b""", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
        assertFalse(sql.contains("ispadmin_staging"), sql)
    }

    private fun read(relative: String): String {
        val path = root.resolve(relative)
        assertTrue(Files.exists(path), "missing $relative")
        return Files.readString(path)
    }

    private fun repoRoot(): Path {
        var dir = Path.of(System.getProperty("user.dir"))
        repeat(6) {
            if (Files.exists(dir.resolve("core/src/main/resources/application-prod.properties"))) {
                return dir
            }
            dir = dir.parent ?: return Path.of(System.getProperty("user.dir"))
        }
        return Path.of(System.getProperty("user.dir"))
    }
}
