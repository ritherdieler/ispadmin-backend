package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.wispadmin.WispAdminApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.Rollback
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.sin

@SpringBootTest(classes = [WispAdminApplication::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev", "local")
@Tag("local-db")
class TrafficLocalSeedIntegrationTest {

    @Autowired
    private lateinit var sampleRepository: SubscriptionTrafficSampleRepository

    @Test
    @Transactional
    @Rollback(false)
    fun seedDemoTrafficForSubscriptionOne() {
        val subscriptionId = 1
        val hostDeviceId = 8
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        val existing = sampleRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(
            subscriptionId,
            now.minusDays(8),
            now.plusMinutes(1)
        )
        val latestBucket = existing.maxOfOrNull { it.bucketStart }
        if (existing.size >= 24 && latestBucket != null && latestBucket.isAfter(now.minusDays(1))) return

        sampleRepository.deleteBySubscriptionId(subscriptionId)

        val start = now.minusDays(7).withMinute(0).withSecond(0).withNano(0)
        var bucket = start
        var index = 0
        while (!bucket.isAfter(now)) {
            if (sampleRepository.findBySubscriptionIdAndBucketStart(subscriptionId, bucket) == null) {
                val wave = sin(index / 6.0)
                val rxBytes = (50_000_000L + (wave * 20_000_000).toLong()).coerceAtLeast(1_000_000L)
                val txBytes = (10_000_000L + (wave * 4_000_000).toLong()).coerceAtLeast(500_000L)
                val downMbps = 20.0 + wave * 15.0
                val upMbps = 4.0 + wave * 3.0
                sampleRepository.save(
                    SubscriptionTrafficSample(
                        subscriptionId = subscriptionId,
                        hostDeviceId = hostDeviceId,
                        bucketStart = bucket,
                        rxBytesDelta = rxBytes,
                        txBytesDelta = txBytes,
                        avgMbpsDown = downMbps,
                        avgMbpsUp = upMbps,
                        counterReset = false
                    )
                )
            }
            bucket = bucket.plusMinutes(5)
            index++
        }

        val seeded = sampleRepository.findBySubscriptionIdAndBucketStartBetweenOrderByBucketStartAsc(
            subscriptionId,
            now.minusDays(8),
            now.plusMinutes(1)
        )
        assertTrue(seeded.isNotEmpty())
    }
}
