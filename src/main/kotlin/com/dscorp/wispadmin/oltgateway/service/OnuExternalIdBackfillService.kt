package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class OnuExternalIdBackfillResult(
    val scanned: Int = 0,
    val rewritten: Int = 0,
    val collisions: Int = 0
)

open class OnuExternalIdBackfillService(
    private val oltRepository: OltMgrOltRepository,
    private val onuRepository: OltMgrOnuRepository,
    private val properties: OltGatewayProperties
) {

    @Transactional
    open fun backfill(): OnuExternalIdBackfillResult {
        val olt = oltRepository.findByName(properties.oltId).orElse(null)
            ?: return OnuExternalIdBackfillResult()

        var scanned = 0
        var rewritten = 0
        var collisions = 0

        onuRepository.findByOlt_Id(olt.id!!)
            .filter { it.deletedAt == null }
            .forEach { onu ->
                scanned++
                if (OnuExternalIdPolicy.isCanonical(onu.externalId, properties.oltId)) return@forEach

                val canonical = OnuExternalIdPolicy.canonical(
                    properties.oltId,
                    onu.board,
                    onu.port,
                    onu.onuIndex
                )
                val taken = onuRepository.findByExternalId(canonical)
                    .filter { it.id != onu.id }
                    .isPresent
                if (taken) {
                    collisions++
                    logger.warn(
                        "No se reescribe el identificador de la ONU {}: {} ya está ocupado",
                        onu.sn,
                        canonical
                    )
                    return@forEach
                }

                logger.info("Backfill de identificador externo: {} -> {}", onu.externalId, canonical)
                onu.externalId = canonical
                onu.updatedAt = Instant.now()
                onuRepository.save(onu)
                rewritten++
            }

        return OnuExternalIdBackfillResult(scanned = scanned, rewritten = rewritten, collisions = collisions)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OnuExternalIdBackfillService::class.java)
    }
}
