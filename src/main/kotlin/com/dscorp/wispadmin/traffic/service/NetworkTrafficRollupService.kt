package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.entity.NetworkTrafficHourOfDay
import com.dscorp.wispadmin.traffic.entity.NetworkTrafficHourOfDayId
import com.dscorp.wispadmin.traffic.repository.NetworkTrafficHourOfDayRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
open class NetworkTrafficRollupService(
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val networkHourRepository: NetworkTrafficHourOfDayRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(NetworkTrafficRollupService::class.java)
    }

    @Transactional
    open fun rollupRecentDays(days: Int) {
        val today = LocalDate.now()
        (1..days).forEach { offset ->
            rollupDay(today.minusDays(offset.toLong()))
        }
    }

    @Transactional
    open fun rollupDay(day: LocalDate) {
        val from = day.atStartOfDay()
        val to = day.plusDays(1).atStartOfDay()
        val hourlyRows = hourlyRepository.findInBucketRange(from, to)
        if (hourlyRows.isEmpty()) return

        (0..23).forEach { hour ->
            val rows = hourlyRows.filter { it.bucketStart.hour == hour }
            val id = NetworkTrafficHourOfDayId(day, hour)
            if (rows.isEmpty()) {
                if (networkHourRepository.existsById(id)) {
                    networkHourRepository.deleteById(id)
                }
                return@forEach
            }
            networkHourRepository.save(
                NetworkTrafficHourOfDay(
                    bucketDate = day,
                    hourOfDay = hour,
                    rxBytesTotal = rows.sumOf { it.rxBytesTotal },
                    txBytesTotal = rows.sumOf { it.txBytesTotal },
                    activeSubscriptions = rows.map { it.subscriptionId }.distinct().size
                )
            )
        }
        logger.info("Network hour-of-day rollup day={} hourlyRows={}", day, hourlyRows.size)
    }
}
