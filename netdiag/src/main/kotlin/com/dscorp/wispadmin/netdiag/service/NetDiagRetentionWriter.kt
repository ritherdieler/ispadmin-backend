package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagAlertDecisionRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagAlertSuppressionWindowRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagNotificationLogRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagOltLogEventRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Cada lote corre en su propia transacción para no mantener bloqueada una tabla de millones de
 * filas durante toda la purga nocturna.
 */
@Component
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
open class NetDiagRetentionWriter(
    private val oltLogEventRepository: NetDiagOltLogEventRepository,
    private val incidentEventRepository: NetDiagIncidentEventRepository,
    private val alertDecisionRepository: NetDiagAlertDecisionRepository,
    private val notificationLogRepository: NetDiagNotificationLogRepository,
    private val suppressionWindowRepository: NetDiagAlertSuppressionWindowRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun purgeOltLogEvents(cutoff: Instant, batchSize: Int): Int =
        deleteBatch(oltLogEventRepository, oltLogEventRepository.findIdsOlderThan(cutoff, page(batchSize)))

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun purgeIncidentEvents(cutoff: Instant, batchSize: Int): Int =
        deleteBatch(incidentEventRepository, incidentEventRepository.findIdsOlderThan(cutoff, page(batchSize)))

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun purgeAlertDecisions(cutoff: Instant, batchSize: Int): Int =
        deleteBatch(alertDecisionRepository, alertDecisionRepository.findIdsOlderThan(cutoff, page(batchSize)))

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun purgeNotificationLogs(cutoff: Instant, batchSize: Int): Int =
        deleteBatch(notificationLogRepository, notificationLogRepository.findIdsOlderThan(cutoff, page(batchSize)))

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun purgeSuppressionWindows(cutoff: Instant, batchSize: Int): Int =
        deleteBatch(suppressionWindowRepository, suppressionWindowRepository.findIdsOlderThan(cutoff, page(batchSize)))

    private fun page(batchSize: Int) = PageRequest.of(0, batchSize.coerceAtLeast(1))

    private fun <T> deleteBatch(repository: JpaRepository<T, Long>, ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        repository.deleteAllByIdInBatch(ids)
        return ids.size
    }
}
