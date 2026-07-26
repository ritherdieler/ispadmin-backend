package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class MikrotikNetwatchAdapter {

    fun collect(session: MikrotikSession, monitorConfig: TargetMonitorConfig): List<NetwatchSnapshot> {
        val rows = runCatching { session.print("/tool/netwatch") }.getOrElse { return emptyList() }
        val filter = monitorConfig.netwatchNames.map { it.lowercase() }.toSet()
        return rows.mapNotNull { row ->
            val name = row["name"].orEmpty().ifBlank { row["host"].orEmpty() }
            if (name.isBlank()) return@mapNotNull null
            if (filter.isNotEmpty() && !filter.contains(name.lowercase())) return@mapNotNull null
            NetwatchSnapshot(
                name = name,
                host = row["host"].orEmpty(),
                status = row["status"].orEmpty().lowercase(),
                type = row["type"],
                since = row["since"],
                comment = row["comment"]
            )
        }
    }
}
