package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.dto.IncidentsSummaryDto
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * El badge P0 del backoffice consulta el resumen cada pocos segundos por cada admin conectado.
 * Servirlo desde memoria durante una ventana corta evita repetir el agregado por cada llamada.
 */
@Component
class NetDiagIncidentSummaryCache(
    private val properties: NetDiagProperties
) {

    internal var nowMillis: () -> Long = { System.currentTimeMillis() }

    private val entry = AtomicReference<Entry?>(null)

    fun get(loader: () -> IncidentsSummaryDto): IncidentsSummaryDto {
        val ttl = properties.alert.summaryCacheMs
        val now = nowMillis()
        val cached = entry.get()
        if (ttl > 0 && cached != null && now - cached.storedAt < ttl) {
            return cached.value
        }
        val fresh = loader()
        entry.set(Entry(now, fresh))
        return fresh
    }

    fun invalidate() {
        entry.set(null)
    }

    private class Entry(val storedAt: Long, val value: IncidentsSummaryDto)
}
