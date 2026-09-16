package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

class RouterOsClassicApiDeadSurfaceTest {

    @Test
    fun `classic adapter properties remain deprecated not deleted`() {
        val names = RouterOsClientProperties::class.memberProperties.map { it.name }.toSet()
        assertTrue("adapter" in names)
        assertTrue("classic" in names)
        val adapter = RouterOsClientProperties::class.memberProperties.first { it.name == "adapter" }
        val classic = RouterOsClientProperties::class.memberProperties.first { it.name == "classic" }
        assertNotNull(adapter.findAnnotation<Deprecated>())
        assertNotNull(classic.findAnnotation<Deprecated>())
        Class.forName("com.dscorp.wispadmin.routeros.config.RouterOsClientProperties\$ClassicProperties")
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.routeros.adapter.LegrangeClassicAdapter")
        }
        @Suppress("DEPRECATION")
        assertEquals("rest", RouterOsClientProperties().adapter)
        @Suppress("DEPRECATION")
        assertEquals(8728, RouterOsClientProperties().classic.port)
    }

    @Test
    fun `runtime overlays keep deprecated classic keys for binding`() {
        val root = repoRoot()
        val prod = Files.readString(root.resolve("core/src/main/resources/application-prod.properties"))
        assertTrue(prod.contains("router.os.client.adapter=\${ROUTER_OS_CLIENT_ADAPTER:rest}"))
        assertTrue(prod.contains("router.os.client.classic.port=8728"))
        assertTrue(prod.contains("net.diag.mikrotik.fallback-classic="))
        val example = Files.readString(root.resolve("scripts/deploy.config.example"))
        assertTrue(example.contains("ROUTER_OS_CLIENT_ADAPTER"))
    }

    private fun repoRoot(): Path {
        var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(6) {
            if (Files.exists(dir.resolve("core/src/main/resources/application-prod.properties"))) return dir
            dir = dir.parent ?: return dir
        }
        return dir
    }
}
