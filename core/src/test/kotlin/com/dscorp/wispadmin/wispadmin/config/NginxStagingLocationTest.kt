package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class NginxStagingLocationTest {

    @Test
    fun snippet_proxies_staging_http_and_websocket() {
        val root = Path.of(System.getProperty("user.dir"))
        val snippet = Files.readString(root.resolve("scripts/nginx-ispadmin-staging.location.conf"))
        assertTrue(snippet.contains("location /ispadmin-staging/ws"), snippet)
        assertTrue(snippet.contains("location /ispadmin-staging/"), snippet)
        assertTrue(snippet.contains("proxy_pass http://gigafiber_backend"), snippet)
        assertTrue(snippet.contains("websocket-backend.conf"), snippet)
    }
}
