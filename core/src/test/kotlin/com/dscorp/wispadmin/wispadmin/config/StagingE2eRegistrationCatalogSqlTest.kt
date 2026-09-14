package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class StagingE2eRegistrationCatalogSqlTest {

    @Test
    fun seed_copies_registration_catalog_from_prod() {
        val sql = Files.readString(root().resolve("scripts/sql/staging-e2e-registration-catalog.sql"))
        assertTrue(sql.contains("INSERT INTO ispadmin_staging.place"))
        assertTrue(sql.contains("FROM ispadmin.place"))
        assertTrue(sql.contains("ST_GeomFromText(ST_AsText"))
        assertFalse(
            Regex("INSERT INTO ispadmin_staging\\.place\\s+SELECT \\*").containsMatchIn(sql),
            "place copy must not SELECT * (GEOMETRY SRID 4326)",
        )
        assertFalse(sql.contains("WHERE id = 1"))
        assertTrue(sql.contains("INSERT INTO ispadmin_staging.mufa"))
        assertTrue(sql.contains("INSERT INTO ispadmin_staging.nap_box"))
        assertTrue(sql.contains("INSERT INTO ispadmin_staging.plan"))
        assertTrue(sql.contains("FROM ispadmin.plan"))
        assertFalse(
            Regex("INSERT INTO ispadmin_staging\\.plan\\s+SELECT \\*").containsMatchIn(sql),
            "plan copy must name columns (Hibernate order differs from prod)",
        )
        assertFalse(
            Regex("INSERT INTO ispadmin_staging\\.nap_box\\s+SELECT \\*").containsMatchIn(sql),
            "nap_box copy must name columns (Hibernate order differs from prod)",
        )
        assertFalse(
            Regex("INSERT INTO ispadmin_staging\\.network_device\\s+SELECT \\*").containsMatchIn(sql),
            "network_device copy must name columns (Hibernate order differs from prod)",
        )
        assertTrue(sql.contains("INSERT INTO ispadmin_staging.network_device"))
        assertTrue(sql.contains("INSERT INTO ispadmin_staging.user"))
        assertTrue(sql.contains("FROM ispadmin.user"))
        assertFalse(
            Regex("INSERT INTO ispadmin_staging\\.user\\s+SELECT \\*").containsMatchIn(sql),
            "user copy must name columns (Hibernate order differs from prod)",
        )
        assertTrue(sql.contains("s.id = p.id"))
        assertTrue(sql.contains("192.168.250.1/24"))
        assertTrue(sql.contains("ST_Contains"))
        assertTrue(sql.contains("POINT(-77.4107 -11.2156)"))
        assertTrue(sql.contains("INSERT INTO stg_acs.tr069_model_profile"))
        assertTrue(sql.contains("FROM prod_acs.tr069_model_profile"))
        assertTrue(sql.contains("UPDATE stg_acs.tr069_model_profile"))
        assertFalse(sql.contains("INSERT INTO ispadmin_staging.tr069_model_profile"))
        assertTrue(sql.contains("F6600RV9.0.21"))
    }

    @Test
    fun seed_all_script_applies_catalog_and_is_documented() {
        val script = Files.readString(root().resolve("scripts/sql/staging-e2e-seed-all.sh"))
        val doc = Files.readString(root().resolve(".agent-docs/staging-e2e-fixtures.md"))
        assertTrue(script.contains("staging-e2e-registration-catalog.sql"), script)
        assertTrue(script.contains("mysql8033"), script)
        assertTrue(script.contains("ispadmin-staging-acs.war"), script)
        assertTrue(doc.contains("tr069_model_profile"), doc)
        assertTrue(doc.contains("stg_acs"), doc)
        assertTrue(doc.contains("staging-e2e-seed-all.sh"), doc)
        assertTrue(doc.contains("ZTEGDC47BFFD"), doc)
        assertTrue(doc.contains("F6600R"), doc)
        assertTrue(doc.contains("staging-fiber-e2e-runbook.md"), doc)
    }

    private fun root(): Path = Path.of(System.getProperty("user.dir"))
}
