package com.dscorp.wispadmin.oltgateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "olt.gateway")
class OltGatewayProperties {

    var enabled: Boolean = false

    var apiKey: String = ""

    var host: String = "10.11.104.2"

    var port: Int = 22

    var username: String = "olt-admin"

    var password: String = ""

    var oltId: String = "gigafiber-ma5608t"

    var modelCode: String = "MA5608T"

    var commandTimeoutMs: Long = 30000

    val mock: MockProperties = MockProperties()

    val ssh: SshProperties = SshProperties()

    val session: SessionProperties = SessionProperties()

    val inventory: InventoryProperties = InventoryProperties()

    val writes: WritesProperties = WritesProperties()

    val sync: SyncProperties = SyncProperties()

    val reachability: ReachabilityProperties = ReachabilityProperties()

    class ReachabilityProperties {
        var failureThreshold: Int = 2
        var backoffMs: Long = 120_000
    }

    class MockProperties {
        var enabled: Boolean = false
    }

    class WritesProperties {
        var enabled: Boolean = false
        var defaultLineProfileId: Int = 10
        var defaultServiceProfileId: Int = 10
    }

    class SyncProperties {
        var inventoryEnabled: Boolean = true
        var inventoryIntervalMs: Long = 600000
        var inventoryInitialDelayMs: Long = 30000
        var signalEnabled: Boolean = true
        var signalIntervalMs: Long = 600000
        var signalInitialDelayMs: Long = 90000
        var alarmEnabled: Boolean = true
        var alarmIntervalMs: Long = 120000
        var alarmInitialDelayMs: Long = 45000
        var skipWhenWriteRunning: Boolean = true
    }

    class SshProperties {
        var legacyAlgorithms: Boolean = true
    }

    class SessionProperties {
        var poolSize: Int = 1
        var keepaliveEnabled: Boolean = true
        var keepaliveIntervalMs: Long = 60000
        var keepaliveCommand: String = "display clock"
        var healthTimeoutMs: Long = 5000
        var sshIdleTimeoutMinutes: Long = 0
    }

    class InventoryProperties {
        var topologyCacheTtlMs: Long = 600000
        var maxSlotProbe: Int = 7
        var defaultPortsPerGponBoard: Int = 16
        var slotAllProbeTimeoutMs: Long = 15000
    }

    fun isValidApiKey(key: String?): Boolean {
        if (key.isNullOrBlank() || apiKey.isBlank()) return false
        return apiKey == key
    }
}
