package com.dscorp.wispadmin.wispadmin.scripts.genieacs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class GenieAcsPilotProvisionsTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    @Test
    fun inform_declares_acs_credentials_once_without_forcing_spv_every_session() {
        val inform = provisionBlock(
            file = "scripts/genieacs/configure-genieacs-pilot.sh",
            afterId = "inform",
            beforeId = "gigafiber-bootstrap",
        )
        assertFalse(inform.contains("{value: now}"), inform)
        assertTrue(inform.contains("ConnectionRequestPassword"), inform)
        assertTrue(inform.contains("{value: 1}"), inform)
        assertFalse(inform.contains("PeriodicInformInterval"), inform)
        assertFalse(inform.contains("PeriodicInformEnable"), inform)
        assertFalse(inform.contains("PeriodicInformTime"), inform)
        assertTrue(inform.contains("\$set"), "mongosh 7 requires \$set on provision updates: $inform")
    }

    @Test
    fun default_refreshes_wan_and_wifi_without_walking_lan_hosts() {
        val default = provisionBlock(
            file = "scripts/genieacs/configure-genieacs-pilot.sh",
            afterId = "default",
            beforeId = "tplg-router",
        )
        assertFalse(default.contains("Hosts.Host"), default)
        assertTrue(default.contains("ExternalIPAddress"), default)
        assertTrue(default.contains("WLANConfiguration"), default)
        assertTrue(default.contains("ConnectionRequestPassword"), default)
        assertTrue(default.contains("{path: hourly, value: 1}"), default)
    }

    @Test
    fun nbi_apply_script_pushes_the_repo_provisions_instead_of_its_own_copies() {
        val nbi = read("scripts/genieacs/apply-provisions-via-nbi.sh")
        for (file in listOf("inform.js", "gigafiber-bootstrap.js", "default.js", "gf-inform-interval.js")) {
            assertTrue(nbi.contains(file), "apply-provisions-via-nbi.sh must push provisions/$file: $nbi")
        }
        // Embedded copies drift from provisions/*.js; there must be none.
        assertFalse(nbi.contains("declare("), nbi)
        assertTrue(read("scripts/genieacs/apply-provisions.sh").contains("provisions/default.js"))
    }

    @Test
    fun inform_js_does_not_force_management_server_on_every_inform() {
        val inform = read("scripts/genieacs/provisions/inform.js")
        assertFalse(inform.contains("{value: now}"), inform)
        assertTrue(inform.contains("{value: 1}"), inform)
        assertTrue(inform.contains("ConnectionRequestPassword"), inform)
        assertFalse(inform.contains("PeriodicInformInterval"), inform)
        assertFalse(inform.contains("PeriodicInformEnable"), inform)
        assertFalse(inform.contains("PeriodicInformTime"), inform)
    }

    /**
     * 36 of the 67 NBI faults were `too_many_commits` on productClass IGD,
     * which exposes only the TR-098 root. Declaring the other root can never
     * resolve, so it retries until GenieACS gives up on the channel.
     */
    @Test
    fun fleet_provisions_declare_only_the_root_the_cpe_exposes() {
        val pilot = read("scripts/genieacs/configure-genieacs-pilot.sh")
        val sources = listOf("inform.js", "gigafiber-bootstrap.js", "default.js")
            .map { read("scripts/genieacs/provisions/$it") } + listOf(pilot)
        for (source in sources) {
            assertTrue(source.contains("probe.size"), "must probe the root before declaring: $source")
            assertFalse(
                source.contains("declare(\"Device.ManagementServer"),
                "unconditional Device.* declare: $source",
            )
        }
    }

    @Test
    fun default_pins_wan_indices_to_stay_inside_the_script_budget() {
        val default = read("scripts/genieacs/provisions/default.js")
        assertFalse(default.contains("Hosts.Host"), default)
        assertTrue(default.contains("ExternalIPAddress"), default)
        assertFalse(default.contains("WANDevice.*"), "nested wildcards cost a GPN per level: $default")
        assertFalse(default.contains("LANDevice.*"), default)
    }

    @Test
    fun bootstrap_preset_matches_only_factory_bootstrap_event() {
        val configure = read("scripts/genieacs/configure-genieacs-pilot.sh")
        val apply = read("scripts/genieacs/apply-provisions.sh")
        for (source in listOf(configure, apply)) {
            assertTrue(source.contains("\"0 BOOTSTRAP\": true"), source)
            assertFalse(source.contains("\"0 BOOT\": true"), "0 BOOT is not a TR-069 event: $source")
            assertFalse(
                source.contains("\"1 BOOT\": true"),
                "1 BOOT must not be AND-ed into bootstrap: $source",
            )
        }
    }

    @Test
    fun bootstrap_sets_the_inform_interval_to_the_360_cadence_with_jitter() {
        for (source in listOf(
            read("scripts/genieacs/provisions/gigafiber-bootstrap.js"),
            read("scripts/genieacs/configure-genieacs-pilot.sh"),
            read("scripts/genieacs/provisions/gf-inform-interval.js"),
        )) {
            assertTrue(source.contains("1800 + jitter"), "interval must be 1800 s plus jitter: $source")
            assertTrue(source.contains("isLab ? 60"), "lab CPEs must Inform every 60 s: $source")
            assertTrue(source.contains("12345B4641531C0B6"), "VSOL lab serial must be allowlisted: $source")
            assertTrue(source.contains("ZTEGDC47BFFD"), "ZTE lab serial must be allowlisted: $source")
            assertTrue(source.contains("serial === \"ZTEGDC47BFFD\""), "lab detection must compare ZTE serial: $source")
            assertFalse(source.contains("informInterval = 3600"), source)
        }
    }

    @Test
    fun cwmp_auth_does_not_challenge_http_digest() {
        val configure = cwmpAuthBlock("scripts/genieacs/configure-genieacs-pilot.sh")
        assertFalse(configure.contains("AUTH("), configure)
        assertTrue(configure.contains("\"true\""), configure)

        val local = read("scripts/genieacs/start-genieacs-local.sh")
        val localAuth = local.substringAfter("_id: \"cwmp.auth\"").substringBefore("cwmp.deviceOnlineThreshold")
        assertFalse(localAuth.contains("AUTH("), localAuth)
        assertTrue(localAuth.contains("\"true\""), localAuth)
    }

    private fun provisionBlock(file: String, afterId: String, beforeId: String): String {
        val text = read(file)
        val after = """_id: "$afterId""""
        val before = """_id: "$beforeId""""
        val start = text.indexOf(after)
        val end = text.indexOf(before)
        assertTrue(start >= 0 && end > start, "missing $afterId..$beforeId in $file")
        return text.substring(start, end)
    }

    private fun cwmpAuthBlock(file: String): String {
        val text = read(file)
        val start = text.indexOf("""_id: "cwmp.auth"""")
        val end = text.indexOf("cwmp.deviceOnlineThreshold")
        assertTrue(start >= 0 && end > start, "missing cwmp.auth in $file")
        return text.substring(start, end)
    }

    private fun read(relative: String): String {
        val path = root.resolve(relative)
        assertTrue(Files.exists(path), "missing $path")
        return Files.readString(path)
    }
}
