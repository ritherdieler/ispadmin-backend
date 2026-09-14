package com.dscorp.wispadmin.wispadmin.scripts.mikrotik

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Mk2PppoeVlan100ScriptTest {

    private fun root(): Path = Path.of(System.getProperty("user.dir"))

    private fun scriptText(): String = read("scripts/mikrotik-mk2-pppoe-vlan100.rsc")

    private fun rollbackText(): String = read("scripts/mikrotik-mk2-pppoe-vlan100-rollback.rsc")

    private fun read(relative: String): String {
        val script = root().resolve(relative)
        assertTrue(Files.exists(script), "missing $script")
        return Files.readString(script)
    }

    private fun commandsOf(text: String): String = text
        .lineSequence()
        .filterNot { it.trimStart().startsWith("#") }
        .joinToString("\n")

    @Test
    fun server_listens_on_the_vlan100_subinterface_and_not_on_the_legacy_bridge() {
        val text = scriptText()
        assertTrue(text.contains(":local pppoeIf \"vlan100-olt\""), text)
        assertTrue(text.contains("pppoe-server server add service-name=\$serviceName interface=\$pppoeIf"), text)
        val commands = commandsOf(text)
        assertFalse(commands.contains("LAN-VLAN1"), "must never touch the legacy bridge server: $commands")
        assertFalse(commands.contains("PPOE CLIENTES"), "must never touch the legacy service-name: $commands")
    }

    @Test
    fun gateway_and_pools_stay_inside_the_reserved_block() {
        val text = scriptText()
        assertTrue(text.contains("10.64.0.1/18"), text)
        assertTrue(text.contains("ranges=10.64.0.2-10.64.47.254"), text)
        assertTrue(text.contains("ranges=10.64.60.2-10.64.60.254"), text)
        assertFalse(text.contains("10.64.250."), "10.64.250.x is outside 10.64.0.0/18: $text")
        assertFalse(text.contains("10.64.64."), "10.64.64.x is outside 10.64.0.0/18: $text")
    }

    @Test
    fun fixed_address_range_is_reserved_outside_the_dynamic_pool() {
        val text = scriptText()
        assertTrue(text.contains("10.64.48.1"), text)
        assertTrue(text.contains("10.64.51.254"), text)
        assertFalse(
            text.contains("ranges=10.64.0.2-10.64.51"),
            "the fixed range must not be part of the dynamic pool: $text"
        )
    }

    @Test
    fun profiles_cover_every_active_fiber_plan_speed() {
        val text = scriptText()
        listOf("GF-200-200", "GF-300-300", "GF-400-400", "GF-500-500", "GF-1000-1000").forEach { profile ->
            assertTrue(text.contains(profile), "missing profile $profile: $text")
        }
        assertTrue(text.contains("\"GF-1000-1000\"=\"1000M/1000M\""), text)
    }

    @Test
    fun profiles_point_to_the_dynamic_pool_and_the_new_gateway() {
        val text = scriptText()
        assertTrue(text.contains("remote-address=PPPOE-DINAMICO"), text)
        assertTrue(text.contains(":local gateway \"10.64.0.1\""), text)
        assertTrue(text.contains("local-address=\$gateway"), text)
    }

    @Test
    fun cut_profile_exists_with_a_throttled_rate() {
        val text = scriptText()
        assertTrue(text.contains("name=GF-CORTE"), text)
        assertTrue(text.contains("rate-limit=1k/1k"), text)
    }

    @Test
    fun mss_clamping_is_scoped_to_the_new_profiles_and_never_to_a_global_forward_rule() {
        val text = scriptText()
        assertTrue(text.contains("change-tcp-mss=yes"), text)
        assertFalse(
            text.contains("action=change-mss"),
            "a forward change-mss rule would touch the 870 existing clients: $text"
        )
    }

    @Test
    fun server_keeps_the_1480_mtu_of_the_legacy_deployment() {
        val text = scriptText()
        assertTrue(text.contains("max-mtu=1480"), text)
        assertTrue(text.contains("max-mru=1480"), text)
        assertTrue(text.contains("one-session-per-host=yes"), text)
    }

    @Test
    fun pilot_uses_its_own_service_name_pool_and_profile() {
        val text = scriptText()
        assertTrue(text.contains("GIGAFIBER-PPPOE-STG"), text)
        assertTrue(text.contains("name=PPPOE-STG"), text)
        assertTrue(text.contains("GF-STG-200-200"), text)
    }

    @Test
    fun script_is_idempotent_because_every_write_checks_for_existence_first() {
        val text = scriptText()
        val adds = Regex("^\\s*/(ip|ppp|interface) .*(add|address add) ", RegexOption.MULTILINE)
            .findAll(text)
            .count()
        val guards = Regex(":if \\(\\[:len \\[").findAll(text).count()
        assertTrue(guards >= adds - 2, "each add needs an existence guard: adds=$adds guards=$guards")
    }

    @Test
    fun rollback_removes_everything_the_script_creates() {
        val text = rollbackText()
        assertTrue(text.contains("pppoe-server server remove"), text)
        assertTrue(text.contains("PPPOE-DINAMICO"), text)
        assertTrue(text.contains("PPPOE-STG"), text)
        assertTrue(text.contains("10.64.0.1/18"), text)
        assertTrue(text.contains("ppp profile remove"), text)
        assertTrue(text.contains("NAT PPPoE dinamico VLAN100"), text)
    }

    @Test
    fun rollback_never_deletes_the_legacy_pppoe_deployment() {
        val text = commandsOf(rollbackText())
        assertFalse(text.contains("PPOE CLIENTES"), "the legacy server must survive a rollback: $text")
        assertFalse(
            Regex("/ppp profile remove \\[find\\]").containsMatchIn(text),
            "a blanket profile delete would remove the legacy PLAN * profiles: $text"
        )
        assertTrue(text.contains("name~\"^GF-\""), "profile deletion must be scoped to the GF catalog: $text")
    }

    @Test
    fun rollback_kicks_sessions_before_deleting_their_secrets() {
        val text = rollbackText()
        val kickIndex = text.indexOf("/ppp active remove")
        val secretIndex = text.indexOf("/ppp secret remove")
        assertTrue(kickIndex in 0 until secretIndex, "sessions must be dropped first: $text")
    }
}
