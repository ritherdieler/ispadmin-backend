package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DeployDisabledModulesPreflightScriptTest {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun current_staging_properties_pass_fiber_chain() {
        val out = run("--env", "staging")
        assertEquals(0, out.exit, out.combined)
        assertFalse(out.combined.contains("olt.gateway.acs.enabled"), out.combined)
    }

    @Test
    fun current_prod_properties_pass_fiber_chain() {
        val out = run("--env", "prod")
        assertEquals(0, out.exit, out.combined)
        assertTrue(out.combined.contains("cadena FIBER/TR-069 completa"), out.combined)
        assertFalse(out.combined.contains("ADVERTENCIA"), out.combined)
    }

    @Test
    fun missing_gateway_acs_client_aborts_without_confirmation() {
        val props = tmp.resolve("application-staging.properties")
        Files.writeString(
            props,
            """
            gigafiber.subsystems.acs.enabled=true
            gigafiber.subsystems.oltgateway.enabled=true
            olt.gateway.client-enabled=true
            acs.client-enabled=true
            acs.internal-base-url=http://127.0.0.1:8080/ispadmin-staging
            genieacs.enabled=${'$'}{GENIEACS_ENABLED:true}
            """.trimIndent() + "\n",
        )
        val out = run("--env", "staging", "--properties", props.toString())
        assertEquals(1, out.exit, out.combined)
        assertTrue(out.combined.contains("olt.gateway.acs.enabled"), out.combined)
        assertTrue(out.combined.contains("TR-069"), out.combined)
        assertTrue(out.combined.contains("confirmación"), out.combined)
    }

    @Test
    fun yes_flag_continues_after_warning() {
        val props = tmp.resolve("broken.properties")
        Files.writeString(props, "olt.gateway.client-enabled=true\n")
        val out = run("--env", "staging", "--properties", props.toString(), "--yes")
        assertEquals(0, out.exit, out.combined)
        assertTrue(out.combined.contains("olt.gateway.acs.enabled"), out.combined)
        assertTrue(out.combined.contains("Continuando"), out.combined)
    }

    @Test
    fun deploy_sh_runs_preflight_before_tests_or_war_only() {
        val root = Path.of(System.getProperty("user.dir"))
        val script = Files.readString(root.resolve("scripts/deploy.sh"))
        assertTrue(script.contains("deploy-disabled-modules-preflight.sh"), script)
        assertTrue(script.contains("check_disabled_modules"), script)
        listOf("full)", "deploy)", "war-only)").forEach { mode ->
            val modeBlock = script.substringAfter("  $mode").substringBefore("    ;;")
            assertTrue(
                modeBlock.contains("check_disabled_modules"),
                "$mode must scan disabled modules before deploying: $modeBlock",
            )
            val preflightIdx = modeBlock.indexOf("check_disabled_modules")
            val testsIdx = modeBlock.indexOf("run_tests")
            val warOnlyIdx = modeBlock.indexOf("require_existing_wars")
            val firstMutation = listOf(testsIdx, warOnlyIdx, modeBlock.indexOf("setup_djl"), modeBlock.indexOf("build_war"))
                .filter { it >= 0 }
                .minOrNull()
            assertTrue(
                firstMutation != null && preflightIdx >= 0 && preflightIdx < firstMutation,
                "$mode must preflight before tests or remote mutation",
            )
        }
    }

    private data class Proc(val exit: Int, val combined: String)

    private fun run(vararg args: String): Proc {
        val root = Path.of(System.getProperty("user.dir"))
        val script = root.resolve("scripts/deploy-disabled-modules-preflight.sh").toFile()
        val pb = ProcessBuilder("bash", script.absolutePath, *args)
            .directory(root.toFile())
            .redirectErrorStream(true)
        pb.environment().remove("DEPLOY_CONFIRM_DISABLED_MODULES")
        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        return Proc(process.waitFor(), output)
    }
}
