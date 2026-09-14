package com.dscorp.wispadmin.netdiag.adapter

import com.dscorp.wispadmin.netdiag.service.NetDiagRetentionService
import com.dscorp.wispadmin.shared.telemetry.TelemetryRetentionPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnBean(NetDiagRetentionService::class)
class NetDiagTelemetryRetentionAdapter(
    private val service: NetDiagRetentionService,
) : TelemetryRetentionPort {
    override fun name() = "netdiag"
    override fun purgeExpired(now: Instant): Int = service.purgeExpired(now).total()
}
