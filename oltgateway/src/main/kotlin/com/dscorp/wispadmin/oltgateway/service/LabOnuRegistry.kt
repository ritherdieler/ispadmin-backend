package com.dscorp.wispadmin.oltgateway.service

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

data class LabOnuRecord(
    val sn: String,
    val createdAt: Instant,
)

interface LabOnuRegistry {
    fun isLab(sn: String?): Boolean
    fun list(): List<LabOnuRecord>
    fun add(sn: String): LabOnuRecord
    fun remove(sn: String)
}

object LabOnuRegistryHolder {
    private val installed = AtomicReference<LabOnuRegistry?>(null)

    fun install(registry: LabOnuRegistry?) {
        installed.set(registry)
    }

    fun current(): LabOnuRegistry? = installed.get()
}

fun normalizeLabSn(sn: String?): String? {
    if (sn.isNullOrBlank()) return null
    val normalized = sn.filter { it.isLetterOrDigit() }.uppercase()
    return normalized.ifEmpty { null }
}

fun matchesLabToken(serial: String, token: String): Boolean =
    serial == token || serial.endsWith(token)

class InMemoryLabOnuRegistry : LabOnuRegistry {
    private val rows = linkedMapOf<String, LabOnuRecord>()

    override fun isLab(sn: String?): Boolean {
        val normalized = normalizeLabSn(sn) ?: return false
        return rows.keys.any { token -> matchesLabToken(normalized, token) }
    }

    override fun list(): List<LabOnuRecord> = rows.values.toList()

    override fun add(sn: String): LabOnuRecord {
        val normalized = normalizeLabSn(sn) ?: throw IllegalArgumentException("sn is blank")
        return rows.getOrPut(normalized) { LabOnuRecord(normalized, Instant.now()) }
    }

    override fun remove(sn: String) {
        val normalized = normalizeLabSn(sn) ?: return
        rows.remove(normalized)
    }
}
