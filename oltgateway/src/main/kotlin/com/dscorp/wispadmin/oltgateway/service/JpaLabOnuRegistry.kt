package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.entity.OltLabOnu
import com.dscorp.wispadmin.oltgateway.domain.repository.OltLabOnuRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Component
@ConditionalOnProperty(prefix = "gigafiber.subsystems.oltgateway", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'\${oltgateway.datasource.url:}'.trim().length() > 0")
class JpaLabOnuRegistry(
    private val repository: OltLabOnuRepository,
    private val properties: OltGatewayProperties,
) : LabOnuRegistry {

    @PostConstruct
    fun seedAndInstall() {
        properties.writes.labAcsSnSuffixes.split(',').forEach { token ->
            val normalized = normalizeLabSn(token) ?: return@forEach
            if (!repository.existsById(normalized)) {
                repository.save(OltLabOnu(sn = normalized, createdAt = Instant.now()))
            }
        }
        LabOnuRegistryHolder.install(this)
    }

    @PreDestroy
    fun uninstall() {
        if (LabOnuRegistryHolder.current() === this) {
            LabOnuRegistryHolder.install(null)
        }
    }

    override fun isLab(sn: String?): Boolean {
        val normalized = normalizeLabSn(sn) ?: return false
        return repository.findAll().any { row -> matchesLabToken(normalized, row.sn) }
    }

    override fun list(): List<LabOnuRecord> =
        repository.findAll().map { row -> LabOnuRecord(row.sn, row.createdAt) }

    override fun add(sn: String): LabOnuRecord {
        val normalized = normalizeLabSn(sn) ?: throw IllegalArgumentException("sn is blank")
        val existing = repository.findById(normalized).orElse(null)
        if (existing != null) return LabOnuRecord(existing.sn, existing.createdAt)
        val saved = repository.save(OltLabOnu(sn = normalized, createdAt = Instant.now()))
        return LabOnuRecord(saved.sn, saved.createdAt)
    }

    override fun remove(sn: String) {
        val normalized = normalizeLabSn(sn) ?: return
        if (repository.existsById(normalized)) {
            repository.deleteById(normalized)
        }
    }
}
