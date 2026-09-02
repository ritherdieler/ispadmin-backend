package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.exception.CliBusBusyException

class OltCommandExecutor(
    private val cliBus: OltCliBus
) {
    fun run(command: String): String = adhoc { it.execute(command) }

    fun <T> adhoc(block: (HuaweiCliSession) -> T): T = execute(CliJobType.ADHOC, block)

    fun <T> job(type: CliJobType, block: (HuaweiCliSession) -> T): T = execute(type, block)

    fun <T> write(block: (HuaweiCliSession) -> T): T = execute(CliJobType.WRITE, block)

    fun ping(): Long = cliBus.ping()

    private fun <T> execute(type: CliJobType, block: (HuaweiCliSession) -> T): T {
        return when (val result = cliBus.execute(type, block)) {
            is CliBusResult.Ok -> result.value
            is CliBusResult.Skipped -> throw CliBusBusyException(result.reason)
        }
    }
}
