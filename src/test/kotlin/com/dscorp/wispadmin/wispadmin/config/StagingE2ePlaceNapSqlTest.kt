package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class StagingE2ePlaceNapSqlTest {

    @Test
    fun seed_copies_all_prod_places_and_documents_polygon_fixture() {
        val sql = Files.readString(root().resolve("scripts/sql/staging-e2e-place-nap.sql"))
        assertTrue(sql.contains("INSERT INTO ispadmin_staging.place"))
        assertTrue(sql.contains("FROM ispadmin.place"))
        assertTrue(sql.contains("UPDATE ispadmin_staging.place"))
        assertFalse(
            sql.contains("WHERE id = 1"),
            "place seed must copy the full prod table, not a single id"
        )
        assertTrue(sql.contains("ST_Contains"))
        assertTrue(sql.contains("POINT(-77.4137 -11.2177)"))
        assertTrue(sql.contains("9 de octubre"))
        assertTrue(sql.contains("INSERT INTO ispadmin_staging.mufa"))
        assertTrue(sql.contains("INSERT INTO ispadmin_staging.nap_box"))
        assertTrue(sql.contains("FROM ispadmin.mufa"))
        assertTrue(sql.contains("FROM ispadmin.nap_box"))
    }

    private fun root(): Path = Path.of(System.getProperty("user.dir"))
}
