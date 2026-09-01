package com.dscorp.wispadmin.netdiag.port

sealed class OltCliOutcome {
    data class Ok(val raw: String) : OltCliOutcome()
    data class Skipped(val reason: String) : OltCliOutcome()
}

interface NetDiagOltCliPort {
    fun runAlarmPoll(): OltCliOutcome
}
