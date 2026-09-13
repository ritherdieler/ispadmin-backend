package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LocalPrestagingE2eScriptTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))
    private val scriptPath: Path = root.resolve("scripts/e2e_register_fiber_local_prestaging.sh")

    private fun script(): String {
        assertTrue(
            Files.exists(scriptPath),
            "missing $scriptPath (/scripts/ is gitignored; git add -f when committing)",
        )
        return Files.readString(scriptPath)
    }

    @Test
    fun e2e_script_targets_local_prestaging_core_and_local_acs() {
        val script = script()
        assertTrue(script.contains("http://127.0.0.1:8082/ispadmin"), script)
        assertTrue(script.contains("http://127.0.0.1:8090/ispadmin-acs"), script)
        assertTrue(script.contains("http://127.0.0.1:8080/ispadmin"), script)
        assertTrue(script.contains("127.0.0.1:7557"), script)
        assertTrue(script.contains("10.11.104.2"), script)
        assertTrue(script.contains("ZTEGDC47BFFD"), script)
        assertTrue(script.contains("HOST_DEVICE_ID=\"\${HOST_DEVICE_ID:-8}\""), script)
        assertTrue(script.contains("VLAN=\"\${VLAN:-100}\""), script)
        assertTrue(script.contains("NAP_BOX_ID=\"\${NAP_BOX_ID:-42}\""), script)
        assertTrue(script.contains("ispadmin_prestaging"), script)
        assertTrue(script.contains("prestaging_oltgateway"), script)
        assertTrue(script.contains("application-local-prestaging.secrets.properties"), script)
        assertTrue(script.contains("/subscription"), script)
        assertTrue(script.contains("registration-progress"), script)
        assertTrue(script.contains("tr069ProvisionStatus"), script)
        assertTrue(script.contains("oltReachable"), script)
        assertTrue(script.contains("/api/acs/v1/health"), script)
        assertTrue(script.contains("run-local-prestaging.sh"), script)
    }

    @Test
    fun e2e_script_rejects_vps_staging_dev_schema_and_smartolt() {
        val script = script()
        assertFalse(script.contains("127.0.0.1:8091"), script)
        assertFalse(script.contains("ispadmin-staging-acs"), script)
        assertFalse(script.contains("ispadmin_dev"), script)
        assertFalse(script.contains("SMARTOLT"), script)
        assertFalse(script.contains("application-local.properties"), script)
        assertFalse(script.contains("NO-001"), script)
        assertFalse(script.contains("/api/olt-gateway/onu/activate"), script)
    }

    @Test
    fun e2e_script_wifi_cleanup_and_lab_only() {
        val script = script()
        assertTrue(script.contains("--wifi-ssid)"), script)
        assertTrue(script.contains("--wifi-pass)"), script)
        assertTrue(script.contains("--cleanup-mode)"), script)
        assertTrue(script.contains("ask|auto|skip"), script)
        assertTrue(script.contains("CLEANUP_MODE=\"\${CLEANUP_MODE:-skip}\""), script)
        assertTrue(script.contains("E2E_WIFI_SSID_5=\"\${E2E_WIFI_SSID} - 5G\""), script)
        assertTrue(script.contains("wifi_24 ssid=\$E2E_WIFI_SSID password=\$E2E_WIFI_PASS"), script)
        assertTrue(script.contains("wifi_5 ssid=\$E2E_WIFI_SSID_5 password=\$E2E_WIFI_PASS"), script)
        val wifiBlock = script.indexOf("== WiFi credentials (before cleanup) ==")
        val cleanupBlock = script.indexOf("== post cleanup")
        assertTrue(wifiBlock > -1, script)
        assertTrue(cleanupBlock > wifiBlock, script)
        assertTrue(script.contains("¿Ejecutar hard cleanup ahora?"), script)
        assertTrue(script.contains("lab"), script)
        assertTrue(script.contains("_tags"), script)
    }

    @Test
    fun e2e_script_is_executable_and_help_mentions_check() {
        assertTrue(Files.isExecutable(scriptPath), "not executable: $scriptPath")
        val script = script()
        assertTrue(script.contains("check"), script)
        assertTrue(script.contains("actuator/health"), script)
        assertTrue(script.contains("ping"), script)
    }

    @Test
    fun e2e_script_is_documented_in_lab_runbook() {
        val doc = Files.readString(root.resolve(".agent-docs/pruebas-local-gateway-acs-lab.md"))
        assertTrue(doc.contains("e2e_register_fiber_local_prestaging.sh"), doc)
        assertTrue(doc.contains("git add -f"), doc)
        assertTrue(doc.contains("--wifi-ssid"), doc)
        assertTrue(doc.contains("8082"), doc)
        assertTrue(doc.contains("8090"), doc)
        val e2eSection = doc.substring(doc.indexOf("### E2E alta FIBER"))
        assertFalse(e2eSection.contains("ispadmin_dev"), e2eSection)
    }
}
