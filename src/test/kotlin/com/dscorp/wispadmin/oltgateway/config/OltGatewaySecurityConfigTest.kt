package com.dscorp.wispadmin.oltgateway.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class OltGatewaySecurityConfigTest {

    @Test
    fun `oltgateway profile permits all so X-Olt-Gateway-Key is the gate`() {
        val root = Path.of(System.getProperty("user.dir"))
        val src = Files.readString(
            root.resolve("src/main/kotlin/com/dscorp/wispadmin/oltgateway/config/OltGatewaySecurityConfig.kt")
        )
        assertTrue(src.contains("@Profile(\"oltgateway\")"), src)
        assertTrue(src.contains("csrf().disable()"), src)
        assertTrue(src.contains("permitAll()"), src)
    }
}
