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

class OltGatewayCommandService(
    private val runCommand: (String) -> String,
    private val properties: OltGatewayProperties,
    private val inWriteJob: (() -> Unit) -> Unit = { it() }
) {

    fun planAuthorize(request: AuthorizeCliRequest): List<String> = listOf(
        "interface gpon 0/${request.board}",
        "ont add ${request.port} ${request.ontId} sn-auth ${request.sn} omci " +
            "ont-lineprofile-id ${request.lineProfileId} ont-srvprofile-id ${request.serviceProfileId} " +
            "desc ${sanitizeDesc(request.description)}",
        "quit",
        "service-port vlan ${request.vlan} gpon 0/${request.board}/${request.port} ont ${request.ontId} " +
            "gemport 1 multi-service user-vlan ${request.vlan} tag-transform translate"
    )

    fun authorize(request: AuthorizeCliRequest): AuthorizeCliResult {
        ensureWritesEnabled()
        val planned = planAuthorize(request)
        val executed = mutableListOf<String>()
        inWriteJob {
            planned.forEach { cmd ->
                executed.add(cmd)
                runCommand(cmd)
            }
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
