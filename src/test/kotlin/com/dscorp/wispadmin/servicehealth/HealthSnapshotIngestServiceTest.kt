package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.events.LiveTelemetryPort
import com.dscorp.wispadmin.events.LiveTrafficSample
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.servicehealth.service.HealthSnapshotIngestService
import com.dscorp.wispadmin.servicehealth.service.HealthSummaryQueryService
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

class HealthSnapshotIngestServiceTest {
    private val identity = mockk<IdentityService>()
    private val summaries = mockk<HealthSummaryQueryService>(relaxed = true)
    private val live = mockk<LiveTelemetryPort>(relaxed = true)
    private val liveProvider = mockk<ObjectProvider<LiveTelemetryPort>>()
    private val service = HealthSnapshotIngestService(identity, summaries, liveProvider, ObjectMapper(), mockk(relaxed = true))

    init {
        every { liveProvider.ifUnique } returns live
        every { identity.resolveOnu("ZTEGDC47BFFD") } returns 42
    }

    @Test
    fun `traffic latest writes live hash and reevaluates`() {
        service.apply(
            PlatformEvent(
                type = PlatformEventTypes.TRAFFIC_LATEST,
                subscriptionId = 42,
                occurredAt = Instant.parse("2026-09-03T18:10:00Z"),
                payloadJson = """{"mbpsDown":8.1,"mbpsUp":0.4,"sampleStatus":"OK","hostDeviceId":9}""",
            )
        )
        verify {
            live.putTraffic(
                42,
                match<LiveTrafficSample> { it.avgMbpsDown == 8.1 && it.avgMbpsUp == 0.4 },
            )
        }
        verify { summaries.reevaluate(42, Instant.parse("2026-09-03T18:10:00Z")) }
    }

    @Test
    fun `optical event resolves sn then reevaluates`() {
        service.apply(
            PlatformEvent(
                type = PlatformEventTypes.ONU_OPTICAL,
                sn = "ZTEGDC47BFFD",
                occurredAt = Instant.parse("2026-09-03T18:10:00Z"),
                payloadJson = """{"rxPowerDbm":-28.4,"runState":"online"}""",
            )
        )
        verify { live.putOnu(42, match { it.rxPowerDbm == -28.4 && it.runState == "online" }) }
        verify { summaries.reevaluate(42, Instant.parse("2026-09-03T18:10:00Z")) }
    }

    @Test
    fun `cpe provisioning updates coarse flags by serial`() {
        val flags = mockk<com.dscorp.wispadmin.wispadmin.service.CpeProvisionFlagService>(relaxed = true)
        val flagsProvider = mockk<ObjectProvider<com.dscorp.wispadmin.wispadmin.service.CpeProvisionFlagService>>()
        every { flagsProvider.ifAvailable } returns flags
        val ingest = HealthSnapshotIngestService(identity, summaries, liveProvider, ObjectMapper(), flagsProvider)
        ingest.apply(
            PlatformEvent(
                type = PlatformEventTypes.CPE_PROVISIONING,
                sn = "ZTEGDC47BFFD",
                occurredAt = Instant.parse("2026-09-03T18:10:00Z"),
                payloadJson = """{"cpeStatus":"COMPLETE","uniqueExternalId":"ext-1"}""",
            )
        )
        verify { flags.apply("ZTEGDC47BFFD", "COMPLETE", any(), any()) }
        verify { summaries.reevaluate(42, Instant.parse("2026-09-03T18:10:00Z")) }
    }
}
