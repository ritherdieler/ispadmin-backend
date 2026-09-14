package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagOltLogEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagOltLogEventRepository
import com.dscorp.wispadmin.netdiag.dto.OltLogEventDto
import com.dscorp.wispadmin.netdiag.dto.OltLogPageDto
import com.dscorp.wispadmin.netdiag.exception.NetDiagConflictException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.format.DateTimeParseException

@Service
@ConditionalOnProperty(prefix = "net.diag", name = ["enabled"], havingValue = "true")
class NetDiagOltLogQueryService(
    private val logEventRepository: NetDiagOltLogEventRepository
) {

    fun listLogs(
        board: Int?,
        port: Int?,
        unparsedOnly: Boolean,
        dateFrom: String?,
        dateTo: String?,
        page: Int,
        size: Int
    ): OltLogPageDto {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "receivedAt"))
        val result = logEventRepository.search(
            board = board,
            port = port,
            unparsedOnly = unparsedOnly,
            fromAt = parseOptionalInstant(dateFrom, "dateFrom"),
            toAt = parseOptionalInstant(dateTo, "dateTo"),
            pageable = pageable
        )
        return OltLogPageDto(
            items = result.content.map { toDto(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    private fun toDto(event: NetDiagOltLogEvent): OltLogEventDto {
        return OltLogEventDto(
            id = event.id ?: 0L,
            receivedAt = event.receivedAt,
            sourceIp = event.sourceIp,
            reasonCode = event.reasonCode,
            board = event.board,
            port = event.port,
            onuIndex = event.onuIndex,
            targetId = event.targetId,
            severity = event.severity,
            incidentId = event.incidentId,
            channel = event.channel,
            alarmIdHex = event.alarmIdHex,
            alarmName = event.alarmName,
            component = event.component,
            isClear = event.isClear,
            isUnparsed = event.isUnparsed,
            rawMessage = event.rawMessage
        )
    }

    private fun parseOptionalInstant(raw: String?, field: String): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            throw NetDiagConflictException("Invalid $field: $raw")
        }
    }
}
