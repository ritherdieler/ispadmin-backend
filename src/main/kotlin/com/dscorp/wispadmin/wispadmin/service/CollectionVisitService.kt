package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.CollectionVisitLog
import com.dscorp.wispadmin.wispadmin.dto.CollectionVisitCommentDto
import com.dscorp.wispadmin.wispadmin.dto.CollectionVisitLogDto
import com.dscorp.wispadmin.wispadmin.dto.CollectionVisitRequestDto
import com.dscorp.wispadmin.wispadmin.repository.CollectionVisitLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CollectionVisitService(
    private val collectionVisitLogRepository: CollectionVisitLogRepository,
    private val subscriptionRepository: SubscriptionRepository,
) {

    @Transactional
    fun registerVisit(request: CollectionVisitRequestDto): CollectionVisitLogDto {
        validateVisitRequest(request)

        val subscription = subscriptionRepository.findById(request.clientId).orElse(null)

        val log = CollectionVisitLog(
            subscriptionId = subscription?.id,
            clientId = request.clientId,
            routeType = request.routeType.trim().ifEmpty { "sector" },
            zoneName = request.zoneName?.trim()?.takeIf { it.isNotEmpty() },
            collectorUserId = request.collectorUserId,
            status = request.status.trim().lowercase(),
            comment = request.comment?.trim()?.takeIf { it.isNotEmpty() },
            latitude = request.latitude,
            longitude = request.longitude,
            visitedAt = LocalDateTime.now(),
        )

        return collectionVisitLogRepository.save(log).toDto()
    }

    @Transactional(readOnly = true)
    fun getRecentVisits(clientId: Int, since: LocalDateTime? = null): List<CollectionVisitLogDto> {
        val effectiveSince = since ?: defaultCommentLookbackSince()
        return collectionVisitLogRepository
            .findCommentsByClientIdSince(clientId, effectiveSince)
            .map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getRecentCommentsForClients(
        clientIds: Collection<Int>,
        since: LocalDateTime,
    ): Map<Int, List<CollectionVisitCommentDto>> {
        if (clientIds.isEmpty()) {
            return emptyMap()
        }

        val logs = collectionVisitLogRepository.findCommentsByClientIdsSince(clientIds, since)
        val grouped = linkedMapOf<Int, MutableList<CollectionVisitCommentDto>>()
        logs.forEach { log ->
            val comment = log.comment?.trim().orEmpty()
            if (comment.isEmpty()) {
                return@forEach
            }
            grouped.getOrPut(log.clientId) { mutableListOf() }.add(log.toCommentDto())
        }
        return grouped
    }

    fun defaultCommentLookbackSince(now: LocalDateTime = LocalDateTime.now()): LocalDateTime =
        now.minusMonths(VISIT_COMMENT_LOOKBACK_MONTHS.toLong())

    /**
     * Devuelve la visita mas reciente por cliente desde [since].
     * Si [since] es null, usa el inicio del dia calendario (comportamiento legacy).
     */
    @Transactional(readOnly = true)
    fun getLatestVisitsForClients(
        clientIds: Collection<Int>,
        since: LocalDateTime? = null,
    ): Map<Int, CollectionVisitLog> {
        if (clientIds.isEmpty()) {
            return emptyMap()
        }

        val effectiveSince = since ?: LocalDate.now().atStartOfDay()
        val logs = collectionVisitLogRepository.findRecentByClientIdsSince(clientIds, effectiveSince)
        val latestByClient = linkedMapOf<Int, CollectionVisitLog>()
        logs.forEach { log ->
            if (log.clientId !in latestByClient) {
                latestByClient[log.clientId] = log
            }
        }
        return latestByClient
    }

    private fun validateVisitRequest(request: CollectionVisitRequestDto) {
        val status = request.status.trim().lowercase()
        require(status in VALID_STATUSES) {
            "Estado de visita invalido: ${request.status}"
        }

        if (status in COMMENT_REQUIRED_STATUSES) {
            require(!request.comment.isNullOrBlank()) {
                "El comentario es obligatorio para el estado $status"
            }
        }
    }

    private fun CollectionVisitLog.toDto(): CollectionVisitLogDto {
        return CollectionVisitLogDto(
            id = id ?: 0,
            subscriptionId = subscriptionId,
            clientId = clientId,
            routeType = routeType,
            zoneName = zoneName,
            collectorUserId = collectorUserId,
            status = status,
            comment = comment,
            latitude = latitude,
            longitude = longitude,
            visitedAt = visitedAt,
        )
    }

    private fun CollectionVisitLog.toCommentDto(): CollectionVisitCommentDto {
        return CollectionVisitCommentDto(
            visitedAt = visitedAt,
            status = status,
            comment = comment?.trim().orEmpty(),
            collectorUserId = collectorUserId,
        )
    }

    companion object {
        const val VISIT_COMMENT_LOOKBACK_MONTHS = 2
        private val VALID_STATUSES = setOf("visited", "no_payment", "not_found", "skipped")
        private val COMMENT_REQUIRED_STATUSES = setOf("no_payment", "not_found")

        fun mapVisitStatus(logStatus: String?): String {
            return when (logStatus?.lowercase()) {
                "skipped" -> "skipped"
                "visited", "no_payment", "not_found" -> "visited"
                else -> "pending"
            }
        }
    }
}
