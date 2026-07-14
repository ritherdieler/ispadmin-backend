package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.DbQueryAggregateDto
import com.dscorp.wispadmin.observability.dto.NPlusOneCandidateDto
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class ObsDatabaseQueryService(
    private val spanRepository: ObsSpanRepository
) {

    fun topQueries(from: Long?, to: Long?, limit: Int, release: String? = null): List<DbQueryAggregateDto> {
        val rows = spanRepository.aggregateDbStatements(from, to, release?.takeIf { it.isNotBlank() }, PageRequest.of(0, limit.coerceIn(1, 500)))
        val grandTotal = rows.sumOf { (it[2] as Number).toLong() }
        return rows.map {
            val total = (it[2] as Number).toLong()
            DbQueryAggregateDto(
                statement = it[0]?.toString(),
                calls = (it[1] as Number).toLong(),
                totalMs = total,
                avgMs = (it[3] as Number).toDouble(),
                maxMs = (it[4] as Number).toLong(),
                pct = if (grandTotal > 0) total.toDouble() / grandTotal else 0.0
            )
        }
    }

    fun nPlusOne(from: Long?, to: Long?, threshold: Long, release: String? = null): List<NPlusOneCandidateDto> {
        val rows = spanRepository.findNPlusOneCandidates(from, to, threshold.coerceAtLeast(1), release?.takeIf { it.isNotBlank() })
        return rows.groupBy { it[1]?.toString() }
            .map { (statement, group) ->
                val best = group.maxByOrNull { (it[2] as Number).toLong() }
                NPlusOneCandidateDto(
                    statement = statement,
                    exampleTraceId = best?.get(0)?.toString(),
                    maxRepetitions = group.maxOf { (it[2] as Number).toLong() },
                    totalMs = group.sumOf { (it[3] as Number).toLong() },
                    affectedTraces = group.size.toLong(),
                    httpRoute = best?.getOrNull(4)?.toString()
                )
            }
            .sortedByDescending { it.maxRepetitions }
    }
}
