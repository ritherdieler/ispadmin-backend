package com.dscorp.wispadmin.netdiag.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ApplicationProdNetDiagPropertiesFileTest {

    @Test
    fun applicationProd_declares_netdiag_and_router_os_client_with_env_placeholders() {
        val root = Path.of(System.getProperty("user.dir"))
        val prod = Files.readString(root.resolve("src/main/resources/application-prod.properties"))
        assertTrue(prod.contains("net.diag.enabled=\${NET_DIAG_ENABLED:"), prod)
        assertTrue(prod.contains("net.diag.api-key=\${NET_DIAG_API_KEY:"), prod)
        assertTrue(prod.contains("router.os.client.rest.trust-store=classpath:routeros-mk-truststore.jks"), prod)
        assertTrue(prod.contains("router.os.client.adapter=\${ROUTER_OS_CLIENT_ADAPTER:rest}"), prod)
        assertTrue(prod.contains("olt.gateway.enabled=\${OLT_GATEWAY_ENABLED:"), prod)
    }
}
