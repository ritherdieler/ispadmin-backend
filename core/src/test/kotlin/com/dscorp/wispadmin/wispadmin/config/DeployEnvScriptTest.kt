package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertEquals
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
        assertTrue(script.contains("build_oltgateway_war"))
        assertTrue(script.contains("deploy_oltgateway_war"))
        assertTrue(script.contains("build_acs_war"))
        assertTrue(script.contains("deploy_acs_war"))
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
        assertTrue(script.contains("verify-djl-war.sh"))
        assertTrue(script.contains("verify-war.sh"))
        assertTrue(script.contains("run_tests()"), "deploy must define a mandatory test gate")
        assertTrue(script.contains("./gradlew test"), "deploy must execute the Gradle test suite")
        assertFalse(script.contains("sh mvnw clean test"), "deploy must not clean with Maven")
        assertTrue(script.contains("deploy-war-needs-rebuild.sh"), "deploy must skip unchanged WAR packages")
        assertTrue(script.contains("war_needs_rebuild"), script)
        assertTrue(script.contains("Skipping WAR package (sources unchanged)"), script)
        listOf("full)", "deploy)").forEach { mode ->
            val modeBlock = script.substringAfter("  $mode").substringBefore("    ;;")
            assertTrue(modeBlock.contains("run_tests"), "$mode must run tests before deploying")
            val firstRemoteMutation = listOf("setup_djl", "init_ssh", "build_war")
                .map(modeBlock::indexOf)
                .filter { it >= 0 }
                .minOrNull()
            assertTrue(firstRemoteMutation != null && modeBlock.indexOf("run_tests") < firstRemoteMutation,
                "$mode must fail on tests before build or remote mutation")
            val restoreIdx = modeBlock.indexOf("restore_host_wars")
            val oltIdx = modeBlock.indexOf("deploy_oltgateway_war")
            val acsIdx = modeBlock.indexOf("deploy_acs_war")
            assertTrue(
                oltIdx >= 0 && restoreIdx > oltIdx,
                "$mode must restore host wars after sibling WARs so prod ispadmin.war survives Tomcat recreate"
            )
            assertTrue(
                acsIdx >= 0 && restoreIdx > acsIdx,
                "$mode must deploy ACS WAR before restoring host wars"
            )
        }
        val warOnlyBlock = script.substringAfter("  war-only)").substringBefore("    ;;")
        assertFalse(warOnlyBlock.contains("run_tests"), "war-only must not clean/rebuild via run_tests")
        assertFalse(warOnlyBlock.contains("build_war"), "war-only must not regenerate WARs")
        assertTrue(warOnlyBlock.contains("require_existing_wars"), "war-only must require packaged WARs in target/")
        val warOnlyRestoreIdx = warOnlyBlock.indexOf("restore_host_wars")
        val warOnlyOltIdx = warOnlyBlock.indexOf("deploy_oltgateway_war")
        val warOnlyAcsIdx = warOnlyBlock.indexOf("deploy_acs_war")
        assertTrue(
            warOnlyOltIdx >= 0 && warOnlyRestoreIdx > warOnlyOltIdx,
            "war-only must restore host wars after sibling WARs"
        )
        assertTrue(
            warOnlyAcsIdx >= 0 && warOnlyRestoreIdx > warOnlyAcsIdx,
            "war-only must deploy ACS WAR before restoring host wars"
        )
        assertFalse(
            script.contains("rm -rf \$CATALINA/webapps/ispadmin \$CATALINA/webapps/\$WAR"),
            "prod exploded dir must not be removed when deploying staging"
        )
    }

    @Test
    fun staging_deploy_uses_dedicated_tomcat_and_never_recreates_prod() {
        val script = Files.readString(root().resolve("scripts/deploy.sh"))
        val example = Files.readString(root().resolve("scripts/deploy.config.example"))
        val nginx = Files.readString(root().resolve("scripts/nginx-ispadmin-staging.location.conf"))
        assertTrue(script.contains("tomcat-staging"), script.take(400))
        assertTrue(script.contains("8081"), "staging Tomcat host port must be 8081")
        assertTrue(example.contains("DOCKER_TOMCAT_STAGING_CONTAINER=tomcat-staging"), example)
        assertTrue(example.contains("TOMCAT_STAGING_HTTP_PORT=8081"), example)
        assertTrue(nginx.contains("gigafiber_backend_staging"), nginx)
        val isolation = script.substringAfter("ensure_war_profile_isolation()").substringBefore("prepare_prod_war_on_host_if_splitting")
        assertTrue(
            isolation.contains("DEPLOY_ENV") && isolation.contains("staging") && isolation.contains("return 0"),
            "staging must skip prod compose recreation",
        )
        val restore = script.substringAfter("restore_host_wars()").substringBefore("ensure_war_profile_isolation")
        assertTrue(restore.contains("ispadmin-staging.war"), restore)
        assertFalse(
            restore.contains("ispadmin.war ispadmin-staging"),
            "staging restore must not copy prod ispadmin.war",
        )
        assertFalse(restore.contains("ispadmin-staging-acs.war"), restore)
        assertFalse(restore.contains("ispadmin-staging-traffic.war"), restore)
        assertFalse(restore.contains("ispadmin-staging-oltgateway.war"), restore)
        val stagingInit = script.substringAfter("if [[ \"\$DEPLOY_ENV\" == \"staging\" ]]; then").substringBefore("else")
        assertTrue(stagingInit.contains("tomcat-staging"), stagingInit)
        assertTrue(script.contains("ensure_tomcat_staging"), "must provision tomcat-staging without touching prod")
        assertTrue(script.contains("compose up -d tomcat-staging") || script.contains("docker compose up -d tomcat-staging"), script)
    }

    @Test
    fun deploy_sh_selects_wars_and_rsyncs_once_with_mtime() {
        val script = Files.readString(root().resolve("scripts/deploy.sh"))
        assertTrue(script.contains("--only"), script.take(400))
        assertTrue(script.contains("deploy-select-wars.sh"), script)
        assertTrue(script.contains("war_selected"), script)
        assertTrue(script.contains("SELECTED_WARS"), script)
        assertTrue(Regex("""rsync -[^\n]*t[^\n]* -e""").containsMatchIn(script) || script.contains("rsync -htW"), script)
        val deployWar = script.substringAfter("deploy_war()").substringBefore("deploy_traffic_war")
        assertFalse(
            deployWar.contains("run_rsync"),
            "deploy_war must docker cp from host after sync_war_to_host, not rsync again",
        )
        assertTrue(
            deployWar.contains("docker restart"),
            "staging WAR redeploy must restart Tomcat so PyTorch JNI is not stuck in a dead classloader",
        )
        val buildWar = script.substringAfter("build_war()").substringBefore("build_traffic_war")
        assertTrue(buildWar.contains("war_selected core") || buildWar.contains("war_selected \"core\""), buildWar)
        assertTrue(script.contains("war_selected traffic") || script.contains("war_selected \"traffic\""), script)
        assertTrue(script.contains("war_selected oltgateway") || script.contains("war_selected \"oltgateway\""), script)
        assertTrue(script.contains("war_selected acs") || script.contains("war_selected \"acs\""), script)
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
            mysqlCalls.all {
                it.contains("\$MYSQL_SCHEMA") ||
                    it.contains("\"\$MYSQL_SCHEMA\"") ||
                    it.contains("\$OLT_GATEWAY_MYSQL_SCHEMA") ||
                    it.contains("\"\$OLT_GATEWAY_MYSQL_SCHEMA\"")
            },
            "every mysql invocation must use MYSQL_SCHEMA or OLT_GATEWAY_MYSQL_SCHEMA, found $mysqlCalls"
        )
        assertTrue(script.contains("OLT_GATEWAY_MYSQL_SCHEMA=\"stg_oltgateway\""))
        assertTrue(script.contains("OLT_GATEWAY_MYSQL_SCHEMA=\"prod_oltgateway\""))
    }

    @Test
    fun tr069_e2e_hard_cleanup_staging_deletes_onu_via_local_olt_gateway() {
        val script = Files.readString(root().resolve("scripts/tr069-e2e-hard-cleanup.sh"))
        assertTrue(
            script.contains("ispadmin-staging"),
            "staging cleanup must call the staging WAR"
        )
        assertTrue(
            script.contains("X-Olt-Gateway-Key"),
            "staging cleanup must send X-Olt-Gateway-Key"
        )
        assertTrue(
            script.contains("/api/olt-gateway/onu/delete/"),
            "staging cleanup must POST Gateway onu delete"
        )
        assertTrue(
            script.contains("\"\$E2E_ENV\" == \"staging\""),
            "must branch on staging for Gateway delete"
        )
        val stagingStart = script.indexOf("\"\$E2E_ENV\" == \"staging\"")
        assertTrue(stagingStart >= 0)
        val afterStaging = script.substring(stagingStart)
        val nextProdBranch = afterStaging.indexOf("\"\$E2E_ENV\" == \"prod\"")
            .takeIf { it >= 0 }
            ?: afterStaging.indexOf("else")
        val stagingSection = if (nextProdBranch > 0) afterStaging.take(nextProdBranch) else afterStaging.take(2500)
        assertTrue(
            stagingSection.contains("127.0.0.1:8081/ispadmin-staging"),
            "host-side staging Gateway is on tomcat-staging port 8081",
        )
        assertFalse(
            stagingSection.contains("gigafiberperu.smartolt.com"),
            "staging branch must not call SmartOLT cloud"
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
        assertTrue(script.contains("PING_ATTEMPTS:-12"), "must wait longer for L2 after ACS COMPLETE")
        assertTrue(script.contains("PING_SLEEP_SECS:-10"), "must space ping retries for ARP")
        assertTrue(script.contains("/rest/ip/arp"), "must dump ARP on ping failure")
        assertTrue(script.contains("sshpass -e"), "must use SSHPASS env for special chars")
    }

    @Test
    fun deploy_sh_rsyncs_face_models_to_opt_gigafiber_models() {
        val script = Files.readString(root().resolve("scripts/deploy.sh"))
        val example = Files.readString(root().resolve("scripts/deploy.config.example"))
        assertTrue(script.contains("/opt/gigafiber/models"), script.take(400))
        assertTrue(script.contains("upload_face_models"), "deploy must define upload_face_models")
        assertTrue(script.contains("core/src/main/resources/models"), script)
        val setupBlock = script.substringAfter("setup_djl()").substringBefore("load_release_version")
        assertTrue(setupBlock.contains("upload_face_models"), "setup must rsync face models")
        val deployBlock = script.substringAfter("  deploy)").substringBefore("    ;;")
        assertTrue(deployBlock.contains("upload_face_models"), "deploy mode must rsync face models")
        assertTrue(example.contains("DOCKER_FACE_MODELS_HOST_DIR=/opt/gigafiber/models"), example)
        assertTrue(
            script.contains("run_rsync \"\$src/\$model\""),
            "run_rsync is not recursive; each model file must be copied",
        )
        assertFalse(
            script.contains("run_rsync \"\$src/\""),
            "directory rsync without -r skips models (rsync: skipping directory)",
        )
    }

    @Test
    fun verify_djl_war_rejects_embedded_face_models() {
        val script = Files.readString(root().resolve("scripts/verify-djl-war.sh"))
        assertTrue(script.contains("core/src/main/resources/models"), script)
        assertTrue(
            script.contains("WEB-INF/classes/models/") && script.contains("fail"),
            "verify-djl-war.sh must fail when models are packed in the WAR",
        )
        assertFalse(
            script.contains("El WAR no contiene WEB-INF/classes/models/"),
            "models must live on disk, not inside the WAR",
        )
        assertTrue(
            script.contains("WEB-INF/classes/com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper.class") &&
                script.contains("PytorchNativeHelper"),
            "helper in WEB-INF/classes loads JNI in the webapp classloader",
        )
    }

    @Test
    fun prestaging_never_deploys_to_the_vps() {
        val root = root()
        val deploy = Files.readString(root.resolve("scripts/deploy.sh"))
        val verify = Files.readString(root.resolve("scripts/verify-war.sh"))
        val war = Files.readString(root.resolve("core/build.gradle.kts"))
        assertTrue(deploy.contains("refuse_prestaging_on_vps"), deploy)
        assertTrue(deploy.contains("local-prestaging no se despliega al VPS"), deploy)
        assertFalse(deploy.contains("--env prestaging"), deploy)
        listOf("full)", "deploy)", "war-only)", "setup)").forEach { mode ->
            val modeBlock = deploy.substringAfter("  $mode").substringBefore("    ;;")
            assertTrue(modeBlock.contains("refuse_prestaging_on_vps"), "$mode must refuse prestaging")
        }
        assertTrue(verify.contains("application-local-prestaging"), verify)
        assertTrue(
            war.contains("application-local-prestaging.properties"),
            war,
        )
        assertTrue(war.contains("application-local-prestaging.secrets.properties"), war)
    }

    @Test
    fun verify_war_sh_accepts_module_jars_in_web_inf_lib() {
        val script = Files.readString(root().resolve("scripts/verify-war.sh"))
        listOf("acs.jar", "oltgateway.jar", "traffic.jar").forEach { jar ->
            assertTrue(script.contains("WEB-INF/lib/$jar"), jar)
        }
        assertTrue(script.contains("WEB-INF/classes/com/dscorp/wispadmin/wispadmin/"), script)
        assertTrue(script.contains("WEB-INF/classes/com/dscorp/wispadmin/servicehealth/"), script)
        assertTrue(script.contains("WEB-INF/classes/com/dscorp/wispadmin/netdiag/"), script)
        assertTrue(script.contains("WEB-INF/classes/com/dscorp/wispadmin/observability/"), script)
        assertTrue(script.contains("WEB-INF/classes/com/dscorp/wispadmin/routeros/"), script)
    }

    @Test
    fun tomcat_staging_compose_sets_prod_staging_profiles() {
        val script = root().resolve("scripts/ensure-tomcat-staging-compose.py")
        val compose = Files.createTempDirectory("tomcat-staging-compose").resolve("docker-compose.yml")
        Files.writeString(
            compose,
            """
            services:
              tomcat-staging:
                container_name: tomcat-staging
                ports:
                  - "8081:8080"
                environment:
                  SPRING_DATASOURCE_PASSWORD: dummy-db-pass
            """.trimIndent() + "\n",
        )
        val proc = ProcessBuilder("python3", script.toString(), compose.toString())
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor(), out)
        val text = Files.readString(compose)
        assertTrue(text.contains("SPRING_PROFILES_ACTIVE: prod,staging"), text)
        assertTrue(text.contains("ACS_DATASOURCE_PASSWORD: dummy-db-pass"), text)
        assertTrue(text.contains("OLTGATEWAY_DATASOURCE_PASSWORD: dummy-db-pass"), text)
        assertTrue(text.contains("TRAFFIC_DATASOURCE_PASSWORD: dummy-db-pass"), text)
    }

    private fun root(): Path = Path.of(System.getProperty("user.dir"))
}
