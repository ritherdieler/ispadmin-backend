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
        assertFalse(
            script.contains("rm -rf \$CATALINA/webapps/ispadmin \$CATALINA/webapps/\$WAR"),
            "prod exploded dir must not be removed when deploying staging"
        )
    }
}
