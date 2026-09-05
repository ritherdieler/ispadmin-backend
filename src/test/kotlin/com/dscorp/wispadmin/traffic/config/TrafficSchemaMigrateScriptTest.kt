package com.dscorp.wispadmin.traffic.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TrafficSchemaMigrateScriptTest {

    @Test
    fun staging_script_copies_samples_and_seeds_routers() {
        val sql = Files.readString(
            Path.of(System.getProperty("user.dir")).resolve("scripts/sql/migrate-traffic-to-stg_traffic.sql")
        )
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS stg_traffic"), sql.take(400))
        assertTrue(sql.contains("ispadmin_staging.subscription_traffic_sample"), sql)
        assertTrue(sql.contains("client_ip"), sql)
        assertTrue(sql.contains("subscription_id"), sql)
        assertTrue(sql.contains("stg_traffic.traffic_router"), sql)
        assertTrue(sql.contains("uk_traffic_sample_ip_bucket"), sql)
        assertTrue(sql.contains("DELETE s1 FROM stg_traffic.subscription_traffic_sample s1"), sql)
        assertTrue(sql.contains("ispadmin_staging.network_device"), sql)
        assertTrue(sql.contains("Do not run twice") || sql.contains("explicit confirmation"), sql)
    }

    @Test
    fun prod_script_targets_prod_traffic() {
        val sql = Files.readString(
            Path.of(System.getProperty("user.dir")).resolve("scripts/sql/migrate-traffic-to-prod_traffic.sql")
        )
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS prod_traffic"), sql.take(400))
        assertTrue(sql.contains("FROM ispadmin.subscription_traffic_sample"), sql)
        assertTrue(sql.contains("prod_traffic.traffic_router"), sql)
        assertTrue(sql.contains("uk_traffic_sample_ip_bucket"), sql)
    }
}
