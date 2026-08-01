package com.dscorp.wispadmin.wispadmin.scripts.netdiag

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class NetDiagMk2TargetSeedSqlTest {

    @Test
    fun mk2_seed_sql_references_wireguard_not_gre() {
        val sql = Path.of(System.getProperty("user.dir"))
            .resolve("scripts/sql/netdiag-target-mk2-wg-seed.sql")
        assertTrue(Files.exists(sql), "missing $sql")
        val text = Files.readString(sql)
        assertTrue(text.contains("wg-ispadmin-vps"), "MK2 seed must monitor wg-ispadmin-vps")
        val mk2Config = text.substringAfter("@mk2_config :=", "").substringBefore("INSERT INTO")
        assertTrue(
            !mk2Config.contains("gre-ispadmin-vps"),
            "MK2 monitor_config must not list gre-ispadmin-vps"
        )
        assertTrue(text.contains("38.224.231.4"), "MK2 seed must bind to MK2 IP")
    }
}
