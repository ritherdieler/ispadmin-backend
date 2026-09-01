package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DeployEnvScriptTest {

    @Test
    fun deploy_sh_supports_staging_war_without_removing_prod_context() {
        val root = Path.of(System.getProperty("user.dir"))
        val script = Files.readString(root.resolve("scripts/deploy.sh"))
        assertTrue(script.contains("--env"), script.take(800))
        assertTrue(script.contains("ispadmin-staging.war"))
        assertTrue(script.contains("staging-war"))
        assertTrue(script.contains("CONTEXT_DIR="))
        assertTrue(script.contains("ensure_war_profile_isolation"))
        assertTrue(script.contains("SPRING_DATASOURCE_URL"))
        assertTrue(script.contains("SPRING_PROFILES_ACTIVE"))
        assertTrue(script.contains("restore_host_wars"))
        assertTrue(script.contains("prepare_prod_war_on_host_if_splitting"))
        assertTrue(script.contains("sync_war_to_host"))
        assertTrue(script.contains("ensure_nginx_staging"))
        assertTrue(script.contains("nginx-ispadmin-staging.location.conf"))
        assertTrue(script.contains("--with"))
        assertTrue(script.contains("subsystems.sh"))
        assertTrue(script.contains("verify-war.sh"))
        assertTrue(script.contains("run_tests()"), "deploy must define a mandatory test gate")
        assertTrue(script.contains("sh mvnw clean test"), "deploy must execute the complete Maven test suite")
        listOf("full)", "war-only)", "deploy)").forEach { mode ->
            val modeBlock = script.substringAfter("  $mode").substringBefore("    ;;")
            assertTrue(modeBlock.contains("run_tests"), "$mode must run tests before deploying")
            val firstRemoteMutation = listOf("setup_djl", "init_ssh", "build_war")
                .map(modeBlock::indexOf)
                .filter { it >= 0 }
                .minOrNull()
            assertTrue(firstRemoteMutation != null && modeBlock.indexOf("run_tests") < firstRemoteMutation,
                "$mode must fail on tests before build or remote mutation")
        }
        assertFalse(
            script.contains("rm -rf \$CATALINA/webapps/ispadmin \$CATALINA/webapps/\$WAR"),
            "prod exploded dir must not be removed when deploying staging"
        )
    }

    @Test
    fun tr069_e2e_hard_cleanup_targets_staging_schema_when_env_staging() {
        val script = Files.readString(root().resolve("scripts/tr069-e2e-hard-cleanup.sh"))
        assertTrue(script.contains("--env"), script.take(400))
        assertTrue(script.contains("ispadmin_staging"))
        assertTrue(script.contains("MYSQL_SCHEMA"))
        assertTrue(script.contains("prod|staging") || script.contains("staging|prod"))
        val mysqlCalls = Regex("""mysql -uroot \S+""").findAll(script).map { it.value }.toList()
        assertTrue(mysqlCalls.isNotEmpty(), "cleanup must invoke mysql")
        assertTrue(
            mysqlCalls.all { it.contains("\$MYSQL_SCHEMA") || it.contains("\"\$MYSQL_SCHEMA\"") },
            "every mysql invocation must use MYSQL_SCHEMA, found $mysqlCalls"
        )
    }

    @Test
    fun tr069_e2e_mk_ping_targets_staging_schema_when_env_staging() {
        val script = Files.readString(root().resolve("scripts/tr069-e2e-mk-ping.sh"))
        assertTrue(script.contains("--env"), script.take(400))
        assertTrue(script.contains("ispadmin_staging"))
        assertTrue(script.contains("MYSQL_SCHEMA"))
        val mysqlCalls = Regex("""mysql -uroot \S+""").findAll(script).map { it.value }.toList()
        assertTrue(mysqlCalls.isNotEmpty(), "mk-ping must invoke mysql")
        assertTrue(
            mysqlCalls.all { it.contains("\$MYSQL_SCHEMA") || it.contains("\"\$MYSQL_SCHEMA\"") },
            "every mysql invocation must use MYSQL_SCHEMA, found $mysqlCalls"
        )
    }

    private fun root(): Path = Path.of(System.getProperty("user.dir"))
}
