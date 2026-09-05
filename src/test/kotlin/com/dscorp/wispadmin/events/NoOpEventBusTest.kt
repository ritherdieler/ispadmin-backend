package com.dscorp.wispadmin.events

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class NoOpEventBusTest {

    @Test
    fun `publish does nothing when redis is disabled`() {
        val bus = NoOpEventBus()
        assertDoesNotThrow {
            bus.publish(
                PlatformEvent(
                    type = PlatformEventTypes.TRAFFIC_LATEST,
                    subscriptionId = 42,
                    sn = "HWTC1234",
                    occurredAt = Instant.parse("2026-09-03T18:10:00Z"),
                    payloadJson = """{"mbpsDown":8.1}""",
                )
            )
        }
    }

    @Test
    fun `codec roundtrips platform event fields`() {
        val original = PlatformEvent(
            type = PlatformEventTypes.ONU_OPTICAL,
            subscriptionId = null,
            sn = "ZTEGDC47BFFD",
            occurredAt = Instant.parse("2026-09-03T18:10:00Z"),
            payloadJson = """{"rxPowerDbm":-28.4}""",
        )
        val fields = PlatformEventCodec.toFields(original)
        assertEquals(PlatformEventTypes.ONU_OPTICAL, fields["type"])
        assertEquals("ZTEGDC47BFFD", fields["sn"])
        assertTrue(fields["subscriptionId"].isNullOrEmpty())
        val restored = PlatformEventCodec.fromFields(fields)
        assertEquals(original.type, restored.type)
        assertEquals(original.sn, restored.sn)
        assertEquals(original.occurredAt, restored.occurredAt)
        assertEquals(original.payloadJson, restored.payloadJson)
        assertEquals(null, restored.subscriptionId)
    }

    @Test
    fun fallbackConfigIsOffWhenRedisIsOn() {
        val root = java.nio.file.Path.of(System.getProperty("user.dir"))
        val source = java.nio.file.Files.readString(
            root.resolve("src/main/kotlin/com/dscorp/wispadmin/events/RedisEventBusConfig.kt")
        )
        assertTrue(!source.contains("class EventBusFallbackConfig"), source)
        assertTrue(source.contains("@ConditionalOnMissingBean(EventBusPort::class)"), source)
        assertTrue(source.contains("@ConditionalOnMissingBean(HealthSnapshotCache::class)"), source)
        assertTrue(source.contains("@ConditionalOnMissingBean(LiveTelemetryPort::class)"), source)
        assertTrue(source.contains("havingValue = \"true\""), source)
        assertTrue(source.contains("@Primary"), source)
    }
}
