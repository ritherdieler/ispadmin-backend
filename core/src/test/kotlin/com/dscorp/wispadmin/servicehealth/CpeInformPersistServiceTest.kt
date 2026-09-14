package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.events.CpeInformStation
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.TelemetryRun
import com.dscorp.wispadmin.servicehealth.domain.WifiCountSample
import com.dscorp.wispadmin.servicehealth.domain.WifiCurrent
import com.dscorp.wispadmin.servicehealth.domain.WifiStationSample
import com.dscorp.wispadmin.servicehealth.repository.TelemetryRunRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiCountSampleRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiCurrentRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiStationSampleRepository
import com.dscorp.wispadmin.servicehealth.service.CpeInformPersistService
import com.dscorp.wispadmin.servicehealth.service.HealthSnapshotIngestService
import com.dscorp.wispadmin.servicehealth.service.HealthSummaryQueryService
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import java.util.Optional

private fun informMapper(): ObjectMapper =
    ObjectMapper().registerModule(JavaTimeModule()).findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

class CpeInformPersistServiceTest {
    private val identity = mockk<IdentityService>()
    private val counts = mockk<WifiCountSampleRepository>(relaxed = true)
    private val stations = mockk<WifiStationSampleRepository>(relaxed = true)
    private val current = mockk<WifiCurrentRepository>(relaxed = true)
    private val runs = mockk<TelemetryRunRepository>(relaxed = true)
    private val properties = ServiceHealthProperties().apply {
        stationHmacKey = "test-only-key-of-at-least-32-bytes-long"
    }
    private val json = informMapper()
    private val service = CpeInformPersistService(identity, counts, stations, current, runs, properties, json)

    private val informAt = Instant.parse("2026-09-08T18:00:00Z")
    private val observedAt = Instant.parse("2026-09-08T17:59:50Z")

    @Test
    fun `unknown sn is discarded without writes`() {
        every { identity.resolveOnu("UNKNOWN") } returns null
        service.persist(
            CpeInformPayload(
                sn = "UNKNOWN",
                deviceId = "dev",
                informAt = informAt,
                complete = true,
                associatedDeviceCount = 0,
                qualityStatus = "FRESH",
                observedAt = observedAt,
            )
        )
        verify(exactly = 0) { counts.upsertAtomic(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { current.save(any()) }
        verify(exactly = 0) { runs.save(any()) }
    }

    @Test
    fun `same subscription and informAt is idempotent`() {
        every { identity.resolveOnu("12345B4641531C0B6") } returns 42
        every { counts.findByDeviceIdAndSubscriptionIdAndInformAt("dev", 42, informAt) } returns
            WifiCountSample(id = 9, subscriptionId = 42, deviceId = "dev", informAt = informAt, observedAt = observedAt)
        service.persist(
            CpeInformPayload(
                sn = "12345B4641531C0B6",
                deviceId = "dev",
                informAt = informAt,
                model = "V2804AX15T",
                complete = true,
                associatedDeviceCount = 1,
                associated5g = 1,
                associated2g = 0,
                qualityStatus = "FRESH",
                observedAt = observedAt,
                stations = listOf(
                    CpeInformStation(macNormalized = "AABBCCDDEEFF", band = "5", observedAt = observedAt, rssi = -40.0),
                ),
            )
        )
        verify(exactly = 0) { counts.upsertAtomic(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { stations.save(any()) }
        verify(exactly = 0) { runs.save(any()) }
    }

    @Test
    fun `complete empty stations still upserts count sample`() {
        every { identity.resolveOnu("12345B4641531C0B6") } returns 42
        every { counts.findByDeviceIdAndSubscriptionIdAndInformAt("dev", 42, informAt) } returns null
        every {
            counts.findIdByDeviceIdAndSubscriptionIdAndObservedAtSql(
                "dev",
                42,
                com.dscorp.wispadmin.servicehealth.domain.UtcInstantText.format(observedAt),
            )
        } returns 11L
        every { current.findById(42) } returns Optional.of(WifiCurrent(subscriptionId = 42))
        every { current.save(any()) } answers { firstArg() }
        every { runs.save(any()) } answers { firstArg() }
        service.persist(
            CpeInformPayload(
                sn = "12345B4641531C0B6",
                deviceId = "dev",
                informAt = informAt,
                model = "V2804AX15T",
                complete = true,
                associatedDeviceCount = 0,
                associated5g = 0,
                associated2g = 0,
                qualityStatus = "FRESH",
                observedAt = observedAt,
                stations = emptyList(),
            )
        )
        verify(exactly = 1) {
            counts.upsertAtomic(
                deviceId = "dev",
                subscriptionId = 42,
                informAt = any(),
                observedAt = any(),
                collectedAt = any(),
                associatedDeviceCount = 0,
                associated2g = 0,
                associated5g = 0,
                lanDeviceCount = null,
                qualityStatus = "FRESH",
                sourceRunId = null,
                errorReason = null,
            )
        }
        verify(exactly = 0) { stations.save(any()) }
        verify { current.save(match { it.associatedDeviceCount == 0 && it.qualityStatus == Quality.FRESH }) }
        val run = slot<TelemetryRun>()
        verify { runs.save(capture(run)) }
        assertEquals("ACS", run.captured.source)
        assertEquals("gateway-cpe", run.captured.equipmentKey)
        assertEquals(informAt, run.captured.startedAt)
        assertEquals(Quality.FRESH, run.captured.qualityStatus)
        assertNotNull(run.captured.completedAt)
    }

    @Test
    fun `persistFromEventJson is transactional for Redis consumer proxy`() {
        val method = CpeInformPersistService::class.java.getMethod("persistFromEventJson", String::class.java)
        org.junit.jupiter.api.Assertions.assertNotNull(
            method.getAnnotation(org.springframework.transaction.annotation.Transactional::class.java),
        )
    }

    @Test
    fun `stations and status current persist when JPA observedAt lookup misses after upsert`() {
        every { identity.resolveOnu("12345B4641531C0B6") } returns 2389
        every { counts.findByDeviceIdAndSubscriptionIdAndInformAt("dev", 2389, informAt) } returns null
        every { counts.upsertAtomic(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
        every { counts.findByDeviceIdAndSubscriptionIdAndObservedAt("dev", 2389, observedAt) } returns null
        every {
            counts.findIdByDeviceIdAndSubscriptionIdAndObservedAtSql(
                "dev",
                2389,
                com.dscorp.wispadmin.servicehealth.domain.UtcInstantText.format(observedAt),
            )
        } returns 3422L
        every { current.findById(2389) } returns Optional.of(WifiCurrent(subscriptionId = 2389))
        every { current.save(any()) } answers { firstArg() }
        every { stations.save(any()) } answers { firstArg() }
        every { runs.save(any()) } answers { firstArg() }

        service.persist(
            CpeInformPayload(
                sn = "12345B4641531C0B6",
                deviceId = "dev",
                informAt = informAt,
                model = "V2804AX15T",
                complete = true,
                associatedDeviceCount = 3,
                associated2g = 2,
                associated5g = 1,
                qualityStatus = "FRESH",
                observedAt = observedAt,
                stations = listOf(
                    CpeInformStation(macNormalized = "AABBCCDDEEFF", band = "5", observedAt = observedAt, rssi = -40.0),
                    CpeInformStation(macNormalized = "112233445566", band = "2.4", observedAt = observedAt, rssi = -55.0),
                ),
            )
        )

        verify(exactly = 2) { stations.save(any()) }
        verify {
            stations.save(match { it.countSampleId == 3422L && it.band == "5" && it.rssi == -40.0 })
        }
        verify {
            stations.save(match { it.countSampleId == 3422L && it.band == "2.4" && it.rssi == -55.0 })
        }
        verify {
            current.save(
                match {
                    it.subscriptionId == 2389 &&
                        it.countSampleId == 3422L &&
                        it.associatedDeviceCount == 3 &&
                        it.qualityStatus == Quality.FRESH &&
                        it.observedAt == observedAt
                }
            )
        }
        verify { runs.save(match { it.source == "ACS" && it.equipmentKey == "gateway-cpe" }) }
    }

    @Test
    fun `falls back to latest count sample id when observed lookups both miss`() {
        every { identity.resolveOnu("12345B4641531C0B6") } returns 2389
        every { counts.findByDeviceIdAndSubscriptionIdAndInformAt("dev", 2389, informAt) } returns null
        every { counts.upsertAtomic(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
        every { counts.findByDeviceIdAndSubscriptionIdAndObservedAt("dev", 2389, observedAt) } returns null
        every { counts.findIdByDeviceIdAndSubscriptionIdAndObservedAtSql(any(), any(), any()) } returns null
        every { counts.findTopByDeviceIdAndSubscriptionIdOrderByInformAtDesc("dev", 2389) } returns
            WifiCountSample(id = 3422, subscriptionId = 2389, deviceId = "dev", informAt = informAt, observedAt = observedAt)
        every { current.findById(2389) } returns Optional.empty()
        every { current.save(any()) } answers { firstArg() }
        every { stations.save(any()) } answers { firstArg() }
        every { runs.save(any()) } answers { firstArg() }

        service.persist(
            CpeInformPayload(
                sn = "12345B4641531C0B6",
                deviceId = "dev",
                informAt = informAt,
                model = "V2804AX15T",
                complete = true,
                associatedDeviceCount = 3,
                associated2g = 2,
                associated5g = 1,
                qualityStatus = "FRESH",
                observedAt = observedAt,
                stations = listOf(
                    CpeInformStation(macNormalized = "AABBCCDDEEFF", band = "5", observedAt = observedAt, rssi = -40.0),
                ),
            )
        )

        verify { stations.save(match { it.countSampleId == 3422L }) }
        verify { current.save(match { it.countSampleId == 3422L && it.associatedDeviceCount == 3 }) }
    }
}

class CpeInformIngestWiringTest {
    @Test
    fun `snapshot ingest routes cpe inform to persist service`() {
        val identity = mockk<IdentityService>()
        every { identity.resolveOnu("12345B4641531C0B6") } returns 42
        val summaries = mockk<HealthSummaryQueryService>(relaxed = true)
        val liveProvider = mockk<ObjectProvider<com.dscorp.wispadmin.events.LiveTelemetryPort>>(relaxed = true)
        val persist = mockk<CpeInformPersistService>(relaxed = true)
        val persistProvider = mockk<ObjectProvider<CpeInformPersistService>>()
        every { persistProvider.ifAvailable } returns persist
        val service = HealthSnapshotIngestService(
            identity,
            summaries,
            liveProvider,
            ObjectMapper().findAndRegisterModules(),
            mockk(relaxed = true),
            persistProvider,
            mockk(relaxed = true),
        )
        val informAt = Instant.parse("2026-09-08T18:00:00Z")
        service.apply(
            PlatformEvent(
                type = PlatformEventTypes.CPE_INFORM,
                sn = "12345B4641531C0B6",
                occurredAt = informAt,
                payloadJson = """{"sn":"12345B4641531C0B6","deviceId":"dev","informAt":"$informAt","complete":true,"associatedDeviceCount":0,"qualityStatus":"FRESH","stations":[]}""",
            )
        )
        verify { persist.persistFromEventJson(any()) }
        verify { summaries.reevaluate(42, informAt) }
    }
}
