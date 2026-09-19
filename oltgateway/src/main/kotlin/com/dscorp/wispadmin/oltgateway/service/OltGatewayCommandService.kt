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
    val vlan: Int,
    val mgmtVlan: Int? = null,
    val mgmtGemport: Int = 2,
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

data class EnsureMgmtServicePortRequest(
    val board: Int,
    val port: Int,
    val ontId: Int,
    val vlan: Int = 1000,
    val gemport: Int = 2,
    val lineProfileId: Int = 12,
)

data class RebootCliRequest(
    val board: Int,
    val port: Int,
    val ontId: Int
)

class OltGatewayCommandService(
    private val runCommand: (String) -> String,
    private val properties: OltGatewayProperties,
    private val inWriteJob: (() -> Unit) -> Unit = { it() },
    private val inAuthorizeJob: (() -> Unit) -> Unit = inWriteJob,
) {

    fun planAuthorize(request: AuthorizeCliRequest): List<String> {
        val commands = mutableListOf(
            "interface gpon 0/${request.board}",
            "ont add ${request.port} ${request.ontId} sn-auth ${request.sn} omci " +
                "ont-lineprofile-id ${request.lineProfileId} ont-srvprofile-id ${request.serviceProfileId} " +
                "desc ${sanitizeDesc(request.description)}",
            "quit",
            servicePortCommand(request.vlan, request.board, request.port, request.ontId, gemport = 1),
        )
        val mgmtVlan = request.mgmtVlan
        if (mgmtVlan != null && mgmtVlan > 0 && mgmtVlan != request.vlan) {
            commands += servicePortCommand(
                vlan = mgmtVlan,
                board = request.board,
                port = request.port,
                ontId = request.ontId,
                gemport = request.mgmtGemport,
            )
        }
        return commands
    }

    fun authorize(request: AuthorizeCliRequest): AuthorizeCliResult {
        ensureWritesEnabled()
        val planned = planAuthorize(request)
        val executed = mutableListOf<String>()
        inAuthorizeJob {
            planned.forEach { cmd ->
                executed.add(cmd)
                val output = runCommand(cmd)
                if (cmd.startsWith("ont add")) {
                    requireOntAddOk(output, cmd)
                } else if (cmd.startsWith("service-port")) {
                    if (looksLikeCliFailure(output)) {
                        throw IllegalStateException("OLT CLI failed for '$cmd': ${output.takeLast(300)}")
                    }
                }
            }
        }
        return AuthorizeCliResult(ontId = request.ontId, commands = executed)
    }

    fun move(request: MoveCliRequest) {
        ensureWritesEnabled()
        inWriteJob {
            runCommand("undo service-port port 0/${request.fromBoard}/${request.fromPort} ont ${request.fromOntId}")
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
            runCommand(servicePortCommand(request.vlan, request.toBoard, request.toPort, request.toOntId))
        }
    }

    private fun servicePortCommand(vlan: Int, board: Int, port: Int, ontId: Int, gemport: Int = 1): String {
        val inbound = properties.writes.inboundTrafficTableIndex
        val outbound = properties.writes.outboundTrafficTableIndex
        return "service-port vlan $vlan gpon 0/$board/$port ont $ontId " +
            "gemport $gemport multi-service user-vlan $vlan tag-transform translate " +
            "inbound traffic-table index $inbound outbound traffic-table index $outbound"
    }

    fun delete(request: DeleteCliRequest) {
        ensureWritesEnabled()
        inWriteJob {
            runChecked("undo service-port port 0/${request.board}/${request.port} ont ${request.ontId}")
            runChecked("interface gpon 0/${request.board}")
            val deleteCmd = "ont delete ${request.port} ${request.ontId}"
            val deleted = runCommand(deleteCmd)
            if (!isOntAlreadyAbsent(deleted)) {
                if (looksLikeCliFailure(deleted)) {
                    throw IllegalStateException("OLT CLI failed for '$deleteCmd': ${deleted.takeLast(300)}")
                }
                requireCliOk(deleted, deleteCmd)
            }
            runChecked("quit")
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

    fun displayServicePorts(board: Int, port: Int, ontId: Int): String =
        runCommand("display service-port port 0/$board/$port ont $ontId")

    fun removeServicePort(board: Int, port: Int, ontId: Int, vlan: Int) {
        ensureWritesEnabled()
        inWriteJob {
            runChecked("undo service-port vlan $vlan gpon 0/$board/$port ont $ontId")
        }
    }

    fun parseServicePortVlans(output: String): Set<Int> =
        VLAN_PATTERN.findAll(output).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()

    fun ensureMgmtServicePort(request: EnsureMgmtServicePortRequest): Set<Int> {
        val before = parseServicePortVlans(displayServicePorts(request.board, request.port, request.ontId))
        if (request.vlan in before) return before
        ensureWritesEnabled()
        inWriteJob {
            runCommand("interface gpon 0/${request.board}")
            val modify = "ont modify ${request.port} ${request.ontId} ont-lineprofile-id ${request.lineProfileId}"
            val modifyOut = runCommand(modify)
            if (looksLikeCliFailure(modifyOut) && !isAlreadyApplied(modifyOut)) {
                throw IllegalStateException("OLT CLI failed for '$modify': ${modifyOut.takeLast(300)}")
            }
            runCommand("quit")
            val sp = servicePortCommand(
                vlan = request.vlan,
                board = request.board,
                port = request.port,
                ontId = request.ontId,
                gemport = request.gemport,
            )
            val spOut = runCommand(sp)
            if (looksLikeCliFailure(spOut) && !isAlreadyApplied(spOut)) {
                throw IllegalStateException("OLT CLI failed for '$sp': ${spOut.takeLast(300)}")
            }
        }
        val after = parseServicePortVlans(displayServicePorts(request.board, request.port, request.ontId))
        if (request.vlan !in after) {
            throw IllegalStateException(
                "OLT did not register VLAN ${request.vlan} on 0/${request.board}/${request.port} ont ${request.ontId}",
            )
        }
        return after
    }

    private fun isAlreadyApplied(output: String): Boolean =
        output.contains(Regex("(?i)already exist"))

    private fun runChecked(command: String): String {
        val output = runCommand(command)
        if (looksLikeCliFailure(output) && !command.startsWith("undo service-port")) {
            throw IllegalStateException("OLT CLI failed for '$command': ${output.takeLast(300)}")
        }
        return output
    }

    private fun requireOntAddOk(output: String, command: String) {
        if (looksLikeCliFailure(output)) {
            throw IllegalStateException("OLT CLI failed for '$command': ${output.takeLast(400)}")
        }
        val confirmed = output.contains(Regex("(?i)success:\\s*[1-9]\\d*"))
        if (!confirmed) {
            throw IllegalStateException("OLT CLI did not confirm ont add for '$command': ${output.takeLast(400)}")
        }
    }

    private fun requireCliOk(output: String, command: String) {
        if (looksLikeCliFailure(output)) {
            throw IllegalStateException("OLT CLI failed for '$command': ${output.takeLast(400)}")
        }
        val confirmed = output.contains(Regex("(?i)success:\\s*[1-9]\\d*")) ||
            output.contains(Regex("(?i)Number of ONTs that can be deleted:\\s*[1-9]\\d*"))
        if (!confirmed) {
            throw IllegalStateException("OLT CLI did not confirm delete for '$command': ${output.takeLast(400)}")
        }
    }

    private fun isOntAlreadyAbsent(output: String): Boolean =
        output.contains(Regex("(?i)The ONT does not exist"))

    private fun looksLikeCliFailure(output: String): Boolean {
        return output.contains(Regex("(?i)Failure:")) ||
            output.contains(Regex("(?i)Parameter error")) ||
            output.contains(Regex("(?i)Error:"))
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

    companion object {
        private val VLAN_PATTERN = Regex("""(?i)vlan\s+(\d+)""")
    }
}
