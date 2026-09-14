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
    fun nbi_apply_script_matches_light_inform_and_default() {
        val nbi = read("scripts/genieacs/apply-provisions-via-nbi.sh")
        val inform = nbi.substringAfter("def provision_inform").substringBefore("def provision_bootstrap")
        val default = nbi.substringAfter("DEFAULT = ").substringBefore("def put_provision")
        assertFalse(inform.contains("value: now"), inform)
        assertTrue(inform.contains("value: 1"), inform)
        assertFalse(inform.contains("PeriodicInformInterval"), inform)
        assertFalse(inform.contains("PeriodicInformEnable"), inform)
        assertFalse(inform.contains("PeriodicInformTime"), inform)
        assertTrue(nbi.contains("PeriodicInformInterval"), nbi)
        assertFalse(default.contains("Hosts.Host"), default)
        assertTrue(default.contains("ExternalIPAddress"), default)
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
