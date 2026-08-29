package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.TrafficAggregationLayer
import com.dscorp.wispadmin.traffic.repository.NetworkTrafficHourOfDayRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficDailyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficFiveMinuteRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficHourlyRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficAggregationWatermarkRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
open class SubscriptionTrafficRetentionService(
    private val sampleRepository: SubscriptionTrafficSampleRepository,
    private val fiveMinuteRepository: SubscriptionTrafficFiveMinuteRepository,
    private val hourlyRepository: SubscriptionTrafficHourlyRepository,
    private val dailyRepository: SubscriptionTrafficDailyRepository,
    private val networkHourRepository: NetworkTrafficHourOfDayRepository,
    private val watermarkRepository: TrafficAggregationWatermarkRepository,
    private val trafficProperties: TrafficProperties
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SubscriptionTrafficRetentionService::class.java)
    }

    data class PurgeResult(
        val rawDeleted: Int,
        val fiveMinuteDeleted: Int,
        val hourlyDeleted: Int,
        val dailyDeleted: Int,
        val networkHourDeleted: Int
    )

    @Transactional
    open fun purgeExpired(): PurgeResult {
        val now = LocalDateTime.now()
        val rawDeleted = deleteRaw(now)
        val fiveMinuteDeleted = deleteFiveMinute(now)
        val hourlyDeleted = deleteHourly(now)
        val dailyDeleted = deleteDaily(now)
        val networkHourDeleted = networkHourRepository.deleteOlderThan(
            now.toLocalDate().minusDays(trafficProperties.retention.networkHourDays.toLong())
        )
        logger.info(
            "Traffic retention raw={} fiveMinute={} hourly={} daily={} networkHour={}",
            rawDeleted,
            fiveMinuteDeleted,
            hourlyDeleted,
            dailyDeleted,
            networkHourDeleted
        )
        return PurgeResult(rawDeleted, fiveMinuteDeleted, hourlyDeleted, dailyDeleted, networkHourDeleted)
    }

    private fun deleteRaw(now: LocalDateTime): Int {
        val consolidated = watermarkRepository.findById(TrafficAggregationLayer.FIVE_MINUTE).orElse(null)?.consolidatedThrough
            ?: return 0
        val ageThreshold = now.minusDays(trafficProperties.retention.rawDays.toLong())
        val threshold = minOf(ageThreshold, consolidated)
        return sampleRepository.deleteOlderThan(threshold)
    }

    private fun deleteFiveMinute(now: LocalDateTime): Int {
        val consolidated = watermarkRepository.findById(TrafficAggregationLayer.HOURLY).orElse(null)?.consolidatedThrough
            ?: return 0
        val ageThreshold = now.minusDays(trafficProperties.retention.fiveMinuteDays.toLong())
        val threshold = minOf(ageThreshold, consolidated)
        return fiveMinuteRepository.deleteOlderThan(threshold)
    }

    private fun deleteHourly(now: LocalDateTime): Int {
        val consolidated = watermarkRepository.findById(TrafficAggregationLayer.DAILY).orElse(null)?.consolidatedThrough
            ?: return 0
        val ageThreshold = now.minusDays(trafficProperties.retention.hourlyDays.toLong())
        val threshold = minOf(ageThreshold, consolidated)
        return hourlyRepository.deleteOlderThan(threshold)
    }

    private fun deleteDaily(now: LocalDateTime): Int {
        val ageThreshold = now.toLocalDate().minusDays(trafficProperties.retention.dailyDays.toLong())
        return dailyRepository.deleteOlderThan(ageThreshold)
    }
}
