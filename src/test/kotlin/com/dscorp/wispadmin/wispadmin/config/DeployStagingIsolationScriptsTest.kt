package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DeployStagingIsolationScriptsTest {

    @Test
    fun composeHelperAddsTomcatStagingWithoutTouchingProd(@TempDir tmp: Path) {
        val compose = tmp.resolve("docker-compose.yml")
        Files.writeString(
            compose,
            """
            services:
              mysql:
                image: mysql:8
              tomcat:
                container_name: tomcat9027
                image: gigafiber/tomcat:9.0.27-custom
                ports:
                  - "8080:8080"
                volumes:
                  - /opt/gigafiber/models:/opt/gigafiber/models:ro
              redis:
                image: redis:7
            """.trimIndent() + "\n",
        )
        val result = runPython("scripts/ensure-tomcat-staging-compose.py", compose.toString())
        assertTrue(result.contains("added"), result)
        val text = Files.readString(compose)
        assertTrue(text.contains("  tomcat-staging:"), text)
        assertTrue(text.contains("container_name: tomcat-staging"), text)
        assertTrue(text.contains("8081:8080"), text)
        val prodBlock = text.substringAfter("  tomcat:").substringBefore("  tomcat-staging:")
        assertTrue(prodBlock.contains("container_name: tomcat9027"), prodBlock)
        assertTrue(prodBlock.contains("8080:8080"), prodBlock)
        assertFalse(prodBlock.contains("8081:8080"), prodBlock)
        val again = runPython("scripts/ensure-tomcat-staging-compose.py", compose.toString())
        assertTrue(again.contains("already"), again)
        assertTrue(Files.readString(compose).contains("container_name: tomcat9027"))
    }

    @Test
    fun nginxHelperAddsStagingUpstreamAndRetargetsExistingLocations(@TempDir tmp: Path) {
        val conf = tmp.resolve("api.conf")
        Files.writeString(
            conf,
            """
            upstream gigafiber_backend {
                server 127.0.0.1:8080;
            }
            server {
                location /ispadmin/ws {
                    proxy_pass http://gigafiber_backend;
                }
                location /ispadmin-staging/ {
                    include snippets/proxy-backend.conf;
                    proxy_pass http://gigafiber_backend;
                }
                location / {
                    return 404;
                }
            }
            """.trimIndent() + "\n",
        )
        val snippet = tmp.resolve("snippet.conf")
        Files.writeString(snippet, "    location /ispadmin-staging-oltgateway {\n        proxy_pass http://gigafiber_backend_staging;\n    }\n")
        val result = runPython(
            "scripts/rewrite-nginx-staging-upstream.py",
            conf.toString(),
            snippet.toString(),
        )
        assertTrue(result.contains("updated"), result)
        val text = Files.readString(conf)
        assertTrue(text.contains("upstream gigafiber_backend_staging"), text)
        assertTrue(text.contains("server 127.0.0.1:8081"), text)
        val stagingLoc = text.substringAfter("location /ispadmin-staging/").substringBefore("location / {")
        assertTrue(stagingLoc.contains("gigafiber_backend_staging"), stagingLoc)
        assertFalse(
            stagingLoc.contains("proxy_pass http://gigafiber_backend;"),
            stagingLoc,
        )
        val prodWs = text.substringAfter("location /ispadmin/ws").substringBefore("location /ispadmin-staging/")
        assertTrue(prodWs.contains("proxy_pass http://gigafiber_backend;"), prodWs)
    }

    private fun runPython(scriptRel: String, vararg args: String): String {
        val root = Path.of(System.getProperty("user.dir"))
        val pb = ProcessBuilder("python3", root.resolve(scriptRel).toString(), *args)
            .directory(root.toFile())
            .redirectErrorStream(true)
        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        assertTrue(code == 0, "python $scriptRel failed ($code): $output")
        return output
    }
}
