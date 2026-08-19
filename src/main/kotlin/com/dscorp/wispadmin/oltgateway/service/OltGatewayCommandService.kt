package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.exception.OltWritesDisabledException

data class AuthorizeCliRequest(
    val board: Int,
    val port: Int,
    val ontId: Int,
    val sn: String,
    val lineProfileId: Int,
    val serviceProfileId: Int,
    val description: String,
    val vlan: Int
)

data class AuthorizeCliResult(
    val ontId: Int,
    val commands: List<String>
)

data class MoveCliRequest(
    val fromBoard: Int,
    val fromPort: Int,
    val fromOntId: Int,
    val toBoard: Int,
    val toPort: Int,
    val toOntId: Int,
    val sn: String,
    val lineProfileId: Int,
    val serviceProfileId: Int,
    val description: String,
    val vlan: Int
)

data class DeleteCliRequest(
    val board: Int,
    val port: Int,
    val ontId: Int
)

data class RebootCliRequest(
    val board: Int,
    val port: Int,
    val ontId: Int
)

data class UpdateWanCliRequest(
    val board: Int,
    val port: Int,
    val ontId: Int,
    val vlan: Int? = null,
    val ipAddress: String? = null,
    val subnetMask: String? = null,
    val gateway: String? = null,
    val dns1: String? = null,
    val dns2: String? = null
)

class OltGatewayCommandService(
    private val runCommand: (String) -> String,
    private val properties: OltGatewayProperties,
    private val inWriteJob: (() -> Unit) -> Unit = { it() }
) {

    fun authorize(request: AuthorizeCliRequest): AuthorizeCliResult {
        ensureWritesEnabled()
        val executed = mutableListOf<String>()
        inWriteJob {
            fun exec(cmd: String) {
                executed.add(cmd)
                runCommand(cmd)
            }
            exec("interface gpon 0/${request.board}")
            val desc = sanitizeDesc(request.description)
            exec(
                "ont add ${request.port} ${request.ontId} sn-auth ${request.sn} omci " +
                    "ont-lineprofile-id ${request.lineProfileId} ont-srvprofile-id ${request.serviceProfileId} desc $desc"
            )
            exec("quit")
            exec(
                "service-port vlan ${request.vlan} gpon 0/${request.board}/${request.port} ont ${request.ontId} " +
                    "gemport 1 multi-service user-vlan ${request.vlan} tag-transform translate"
            )
        }
        return AuthorizeCliResult(ontId = request.ontId, commands = executed)
    }

    fun move(request: MoveCliRequest) {
        ensureWritesEnabled()
        inWriteJob {
            runCommand("interface gpon 0/${request.fromBoard}")
            runCommand("ont delete ${request.fromPort} ${request.fromOntId}")
            runCommand("quit")
            runCommand("interface gpon 0/${request.toBoard}")
            val desc = sanitizeDesc(request.description)
            runCommand(
                "ont add ${request.toPort} ${request.toOntId} sn-auth ${request.sn} omci " +
                    "ont-lineprofile-id ${request.lineProfileId} ont-srvprofile-id ${request.serviceProfileId} desc $desc"
            )
            runCommand("quit")
            runCommand(
                "service-port vlan ${request.vlan} gpon 0/${request.toBoard}/${request.toPort} ont ${request.toOntId} " +
                    "gemport 1 multi-service user-vlan ${request.vlan} tag-transform translate"
            )
        }
    }

    fun delete(request: DeleteCliRequest) {
        ensureWritesEnabled()
        inWriteJob {
            runCommand("interface gpon 0/${request.board}")
            runCommand("ont delete ${request.port} ${request.ontId}")
            runCommand("quit")
        }
    }

    fun reboot(request: RebootCliRequest) {
        ensureWritesEnabled()
        inWriteJob {
            runCommand("interface gpon 0/${request.board}")
            runCommand("ont reboot ${request.port} ${request.ontId}")
            runCommand("quit")
        }
    }

    /** Retags the ONT service-port and/or rewrites its WAN address over OMCI. */
    fun updateWan(request: UpdateWanCliRequest): List<String> {
        ensureWritesEnabled()
        val executed = mutableListOf<String>()
        val hasStaticIp = request.ipAddress != null && request.subnetMask != null
        if (request.vlan == null && !hasStaticIp) return executed

        inWriteJob {
            fun exec(cmd: String) {
                executed.add(cmd)
                runCommand(cmd)
            }
            request.vlan?.let { vlan ->
                // MA5608T cannot edit a service-port in place: drop the ONT ones and recreate them.
                exec("undo service-port port 0/${request.board}/${request.port} ont ${request.ontId}")
                exec(
                    "service-port vlan $vlan gpon 0/${request.board}/${request.port} ont ${request.ontId} " +
                        "gemport 1 multi-service user-vlan $vlan tag-transform translate"
                )
            }
            if (hasStaticIp) {
                exec("interface gpon 0/${request.board}")
                exec(ipConfigCommand(request))
                exec("quit")
            }
        }
        return executed
    }

    private fun ipConfigCommand(request: UpdateWanCliRequest): String = buildString {
        append("ont ipconfig ${request.port} ${request.ontId} static ")
        append("ip-address ${request.ipAddress} mask ${request.subnetMask}")
        request.gateway?.let { append(" gateway $it") }
        request.dns1?.let { append(" pri-dns $it") }
        request.dns2?.let { append(" slave-dns $it") }
        request.vlan?.let { append(" vlan $it") }
    }

    private fun ensureWritesEnabled() {
        if (!properties.writes.enabled) {
            throw OltWritesDisabledException("OLT gateway writes are disabled (olt.gateway.writes.enabled=false)")
        }
    }

    private fun sanitizeDesc(description: String): String {
        val cleaned = description.replace("\"", "").trim().ifBlank { "onu" }
        return "\"$cleaned\""
    }
}
