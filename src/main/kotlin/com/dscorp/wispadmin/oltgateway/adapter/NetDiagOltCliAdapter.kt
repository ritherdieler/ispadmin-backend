package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.netdiag.port.NetDiagOltCliPort
import com.dscorp.wispadmin.netdiag.port.OltCliOutcome
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.ssh.CliBusResult
import com.dscorp.wispadmin.oltgateway.ssh.CliJobType
import com.dscorp.wispadmin.oltgateway.ssh.OltCliBus
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class NetDiagOltCliAdapter(
    private val cliBusProvider: ObjectProvider<OltCliBus>,
    private val properties: OltGatewayProperties
) : NetDiagOltCliPort {

    override fun runAlarmPoll(): OltCliOutcome {
        val bus = cliBusProvider.ifAvailable ?: return OltCliOutcome.Skipped("cli_bus_unavailable")
        val timeoutMs = properties.sync.alarmCommandTimeoutMs.coerceAtLeast(MIN_ALARM_TIMEOUT_MS)
        return when (val result = bus.execute(CliJobType.ALARM_POLL) { session ->
            session.execute("screen-length 0 temporary")
            session.execute("scroll 512")
            session.execute("display alarm active all", timeoutMs)
        }) {
            is CliBusResult.Ok -> OltCliOutcome.Ok(result.value)
            is CliBusResult.Skipped -> OltCliOutcome.Skipped(result.reason)
        }
    }

    companion object {
        const val MIN_ALARM_TIMEOUT_MS: Long = 30_000
    }
}
