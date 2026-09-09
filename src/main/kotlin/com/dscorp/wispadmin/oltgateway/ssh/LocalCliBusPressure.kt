package com.dscorp.wispadmin.oltgateway.ssh

data class LocalCliBusPressure(
    val localQueueDepth: Int,
    val localBusyJobType: String?,
) {
    companion object {
        const val SSH_ACTIVE_NA = "n/a"
        const val SSH_MAX_NA = "n/a"

        fun snapshot(cliBus: OltCliBus?): LocalCliBusPressure =
            LocalCliBusPressure(
                localQueueDepth = cliBus?.queueDepth() ?: 0,
                localBusyJobType = cliBus?.busyJobType()?.name
            )
    }
}
