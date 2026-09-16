package com.dscorp.wispadmin.oltgateway.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.memberProperties

class OltGatewaySshFallbackDeadSurfaceTest {

    private fun repoRoot(): Path {
        var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(6) {
            if (Files.exists(dir.resolve("core/src/main/resources/application-prod.properties"))) return dir
            dir = dir.parent ?: return dir
        }
        return dir
    }

    @Test
    fun `deprecated ssh inventory and signal fallbacks are gone`() {
        val names = OltGatewayProperties.SnmpProperties::class.memberProperties.map { it.name }.toSet()
        assertFalse("allowSshInventoryFallback" in names)
        assertFalse("allowSshSignalFallback" in names)
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.oltgateway.service.inventory.ParallelOnuInventoryReader")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.oltgateway.service.inventory.InventoryJobPlanner")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.oltgateway.service.inventory.OltGponTopologyDiscovery")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.oltgateway.service.inventory.GponBoardClassifier")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.dscorp.wispadmin.oltgateway.service.inventory.InventoryCliJob")
        }
        val root = repoRoot()
        listOf(
            "core/src/main/resources/application-prod.properties",
            "core/src/main/resources/application-dev.properties",
        ).forEach { name ->
            val text = Files.readString(root.resolve(name))
            assertFalse(text.contains("allow-ssh-inventory-fallback"), name)
            assertFalse(text.contains("allow-ssh-signal-fallback"), name)
            assertFalse(text.contains("OLT_GATEWAY_SNMP_ALLOW_SSH_FALLBACK"), name)
            assertFalse(text.contains("OLT_GATEWAY_SNMP_ALLOW_SSH_SIGNAL_FALLBACK"), name)
        }
    }
}
