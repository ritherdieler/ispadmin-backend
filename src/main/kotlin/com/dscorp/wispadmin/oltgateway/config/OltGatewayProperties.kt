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

    var smartoltOltId: String = ""

    var modelCode: String = "MA5608T"

    var commandTimeoutMs: Long = 30000

    val acs: AcsClientProperties = AcsClientProperties()

    val mock: MockProperties = MockProperties()

    val ssh: SshProperties = SshProperties()

    val session: SessionProperties = SessionProperties()

    val inventory: InventoryProperties = InventoryProperties()

    val writes: WritesProperties = WritesProperties()

    val sync: SyncProperties = SyncProperties()

    val reachability: ReachabilityProperties = ReachabilityProperties()

    val snmp: SnmpProperties = SnmpProperties()

    val autofind: AutofindProperties = AutofindProperties()

    class AutofindProperties {
        /** Sirve /onu/unconfigured_onus desde la caché propia en vez de SmartOLT. */
        var enabled: Boolean = true
        var refreshIntervalMs: Long = 30_000
        var initialDelayMs: Long = 20_000
        /** Timeout duro del refresco forzado desde el botón de campo. */
        var liveTimeoutMs: Long = 10_000
    }

    class ReachabilityProperties {
        var failureThreshold: Int = 2
        var backoffMs: Long = 120_000
    }

    class SnmpProperties {
        /** When true and roCommunity is set, SNMP inventory client bean + sync are available. */
        var enabled: Boolean = false
        var port: Int = 161
        var roCommunity: String = ""
        var timeoutMs: Long = 5000
        var retries: Int = 1
        var maxRepetitions: Int = 25
        /** Small pacing interval between GETBULK pages; protects constrained OLT agents. */
        var requestIntervalMs: Long = 100
        /**
         * Deprecated escape hatch: allow inventory sync via SSH when SNMP is unavailable.
         * Default false — inventory sync is SNMP-only when this module is the intended path.
         */
        var allowSshInventoryFallback: Boolean = false
        /**
         * Deprecated escape hatch: allow optical signal poll via SSH when SNMP is unavailable.
         * Default false — signal poll is SNMP-only.
         */
        var allowSshSignalFallback: Boolean = false
        /** Walk Rx/Tx/OLT-Rx columns concurrently on full-table optical poll. */
        var opticalParallelColumns: Boolean = true
        /** Max concurrent per-port optical walks when [opticalPerPortWalks] is true. */
        var opticalParallelPorts: Int = 3
        /** If true, GETBULK per GPON port (slower on MA5608T). Default false = full-table. */
        var opticalPerPortWalks: Boolean = false
        /** With per-port walks: only ports that have online ONUs in DB. */
        var opticalOnlineOnly: Boolean = true
        /** How long to wait for this OLT's SNMP bus permit before failing. */
        var acquireTimeoutMs: Long = 300_000
        val trap: TrapProperties = TrapProperties()
    }

    class TrapProperties {
        /** ASN.1 SNMPv2c trap listener (Huawei OLT). Off until receptor + target are ready. */
        var enabled: Boolean = false
        var listenPort: Int = 1162
        var bindAddress: String = "0.0.0.0"
        /** If non-blank, ignore traps whose community does not match. */
        var community: String = ""
        var bufferSize: Int = 100
        var dispatcherThreads: Int = 2
    }

    class MockProperties {
        var enabled: Boolean = false
    }

    class WritesProperties {
        var enabled: Boolean = false
        var defaultLineProfileId: Int = 10
        var defaultServiceProfileId: Int = 10
        var customProfileBindings: String = "Generic_1:1=3:2,Generic_1:100=6:13"
        var inboundTrafficTableIndex: Int = 8
        var outboundTrafficTableIndex: Int = 9
    }

    class SyncProperties {
        var inventoryEnabled: Boolean = true
        var inventoryIntervalMs: Long = 600000
        var inventoryInitialDelayMs: Long = 30000
        var signalEnabled: Boolean = true
        /** Default 5 min — ~3x SNMP optical walk (~104 s). */
        var signalIntervalMs: Long = 300000
        var signalInitialDelayMs: Long = 90000
        var alarmEnabled: Boolean = true
        var alarmIntervalMs: Long = 120000
        /** El volcado de alarmas corre en el carril de fondo; no debe solaparse con el siguiente ciclo. */
        var alarmCommandTimeoutMs: Long = 120000
        var alarmInitialDelayMs: Long = 45000
        /** Con carriles dedicados el fondo ya no compite con las escrituras. */
        var skipWhenWriteRunning: Boolean = false
        var labOpticalSshEnabled: Boolean = false
        var labOpticalSshIntervalMs: Long = 900000
        var labOpticalSshInitialDelayMs: Long = 120000
    }

    class SshProperties {
        var legacyAlgorithms: Boolean = true
    }

    class SessionProperties {
        /** 2 = carril interactivo + carril de fondo; la 3.a sesion de la OLT queda para operador/backup. */
        var poolSize: Int = 2
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

    class AcsClientProperties {
        var enabled: Boolean = false
        var internalBaseUrl: String = ""
        var apiKey: String = ""
    }

    fun isValidApiKey(key: String?): Boolean {
        if (key.isNullOrBlank() || apiKey.isBlank()) return false
        return apiKey == key
    }
}
