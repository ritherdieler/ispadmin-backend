package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.parser.OnuInfoBySnParser
import org.slf4j.LoggerFactory

data class OmciManagementTarget(val serial: String, val slot: Int, val port: Int, val ontId: Int, val tr069ProfileId: Int) {
    init {
        require(serial.matches(Regex("[A-Z0-9]{12,16}")))
        require(slot in 0..255 && port in 0..255 && ontId in 0..255 && tr069ProfileId in 0..65535)
    }
}
data class OmciManagementEvidence(val configured: Boolean, val address: String?)

/** Called only after the operation has durably captured ownership and its compensation plan. */
class OmciManagementV2(
    private val writeJob: (((String) -> String) -> Unit) -> Unit,
) {
    fun ensure(target: OmciManagementTarget): OmciManagementEvidence {
        logger.info("OMCI ensure start serial={} slot={} port={} ont={} profile={}", target.serial, target.slot, target.port, target.ontId, target.tr069ProfileId)
        var evidence = OmciManagementEvidence(false, null)
        try {
            writeJob { command ->
                checked(command, "interface gpon 0/${target.slot}")
                try {
                    val before = inspect(command, target)
                    if (!before.configured) {
                        checked(command, "ont ipconfig ${target.port} ${target.ontId} ip-index 0 dhcp vlan 1000 priority 2")
                        checked(command, "ont tr069-server-config ${target.port} ${target.ontId} profile-id ${target.tr069ProfileId}")
                    }
                    evidence = if (before.configured) before else inspect(command, target)
                    check(evidence.configured) { "OMCI_READBACK_MISMATCH" }
                } finally {
                    checked(command, "quit")
                }
            }
            logger.info("OMCI ensure done serial={} configured={} addressPresent={}", target.serial, evidence.configured, evidence.address != null)
            return evidence
        } catch (ex: Exception) {
            logger.warn("OMCI ensure failed serial={} reason={}", target.serial, ex.message?.replace(Regex("\\s+"), " ")?.take(400))
            throw ex
        }
    }

    /** Reverts only the compatible DHCP/VLAN-1000 management channel and bound profile. */
    fun remove(target: OmciManagementTarget) {
        logger.info("OMCI remove start serial={} slot={} port={} ont={}", target.serial, target.slot, target.port, target.ontId)
        try {
        writeJob { command ->
            checked(command, "interface gpon 0/${target.slot}")
            try {
                val state = inspectState(command, target)
                if (state.serverProfileId != null && state.serverProfileId != target.tr069ProfileId) {
                    error("OMCI_EXISTING_SERVER_CONFLICT")
                }
                if (state.configType != "DHCP" && state.configType != "Invalid") {
                    error("OMCI_EXISTING_WAN_CONFLICT")
                }
                if (state.configType == "DHCP" &&
                    (state.manageVlan != MANAGEMENT_VLAN || state.managePriority != MANAGEMENT_PRIORITY)
                ) {
                    error("OMCI_EXISTING_WAN_CONFLICT")
                }
                if (state.serverProfileId == target.tr069ProfileId) {
                    checked(command, "undo ont tr069-server-config ${target.port} ${target.ontId}")
                }
                if (state.configType == "DHCP") {
                    checked(command, "undo ont ipconfig ${target.port} ${target.ontId}")
                }
                val after = inspectState(command, target)
                check(after.serverProfileId == null && after.configType == "Invalid") { "OMCI_REMOVE_UNCONFIRMED" }
            } finally {
                checked(command, "quit")
            }
        }
        logger.info("OMCI remove done serial={}", target.serial)
        } catch (ex: Exception) {
            logger.warn("OMCI remove failed serial={} reason={}", target.serial, ex.message?.replace(Regex("\\s+"), " ")?.take(400))
            throw ex
        }
    }

    private fun inspect(command: (String) -> String, target: OmciManagementTarget): OmciManagementEvidence {
        val state = inspectState(command, target)
        check(state.serverProfileId == null || state.serverProfileId == target.tr069ProfileId) { "OMCI_EXISTING_SERVER_CONFLICT" }
        check(state.configType == "DHCP" || state.configType == "Invalid") { "OMCI_EXISTING_WAN_CONFLICT" }
        val configured = state.configType == "DHCP" && state.manageVlan == MANAGEMENT_VLAN &&
            state.managePriority == MANAGEMENT_PRIORITY
        return OmciManagementEvidence(configured && state.serverProfileId == target.tr069ProfileId, state.address)
    }

    private fun inspectState(command: (String) -> String, target: OmciManagementTarget): OmciManagementState {
        val info = checked(command, "display ont info ${target.port} ${target.ontId}")
        val parsed = OnuInfoBySnParser().parse(info)
        check(parsed != null && parsed.sn == target.serial && parsed.slot == target.slot &&
            parsed.frame == 0 && parsed.port == target.port && parsed.ontId == target.ontId) { "OMCI_IDENTITY_MISMATCH" }
        check(field(info, "Run state") == "online") { "OMCI_ONU_OFFLINE" }
        val configState = field(info, "Config state")
        check(configState == "normal") { "OMCI_PROFILE_MISMATCH configState=$configState" }
        check(field(info, "TR069 management") == "Enable" && field(info, "TR069 IP index") == "0") { "OMCI_LINE_PROFILE_NOT_READY" }
        val server = field(info, "TR069 server profile ID")?.takeIf { it.isNotBlank() && it != "-" }?.toIntOrNull()
        val ip = command("display ont ipconfig ${target.port} ${target.ontId}")
        if (hasNoIpInformation(ip)) {
            return OmciManagementState(
                serverProfileId = server,
                configType = "Invalid",
                manageVlan = null,
                managePriority = null,
                address = null,
            )
        }
        checkedOutput(ip, "display ont ipconfig ${target.port} ${target.ontId}")
        val host = hostBlock(ip, "0") ?: error("OMCI_UNEXPECTED_IP_HOSTS")
        val address = field(host, "ONT IP")?.takeIf { validAddress(it) }
        return OmciManagementState(
            serverProfileId = server,
            configType = field(host, "ONT config type"),
            manageVlan = field(host, "ONT manage VLAN")?.toIntOrNull(),
            managePriority = field(host, "ONT manage priority")?.toIntOrNull(),
            address = address,
        )
    }

    private fun checked(command: (String) -> String, value: String): String {
        val output = command(value)
        checkedOutput(output, value)
        return output
    }

    private fun checkedOutput(output: String, value: String) {
        check(!Regex("(?i)failure:|%\\s*(unknown|parameter|error|incomplete|ambiguous)|error:").containsMatchIn(output)) {
            "OMCI_CLI_REJECTED command=$value output=${output.replace(Regex("\\s+"), " ").takeLast(360)}"
        }
    }

    private fun hostBlock(output: String, index: String): String? {
        val start = Regex("(?m)^\\s*ONT IP host index\\s*:\\s*$index\\s*$").find(output) ?: return null
        val next = Regex("(?m)^\\s*ONT IP host index\\s*:").find(output, start.range.last + 1)
        return output.substring(start.range.first, next?.range?.first ?: output.length)
    }

    private fun hasNoIpInformation(output: String): Boolean =
        output.contains("Failure: The ONT does not configure IP information", ignoreCase = true)

    private fun field(output: String, name: String): String? = Regex("(?m)^\\s*${Regex.escape(name)}\\s*:\\s*([^\\r\\n]*)")
        .find(output)?.groupValues?.get(1)?.trim()

    private fun validAddress(address: String): Boolean {
        val octets = address.split('.').map { it.toIntOrNull() }
        return octets.size == 4 && octets.all { it != null && it in 0..255 } && octets.first() in 1..223 && octets.first() != 127
    }

    private data class OmciManagementState(
        val serverProfileId: Int?,
        val configType: String?,
        val manageVlan: Int?,
        val managePriority: Int?,
        val address: String?,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(OmciManagementV2::class.java)
        const val MANAGEMENT_VLAN = 1000
        const val MANAGEMENT_PRIORITY = 2
    }
}
