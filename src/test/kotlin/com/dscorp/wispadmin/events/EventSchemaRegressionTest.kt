package com.dscorp.wispadmin.events
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
class EventSchemaRegressionTest {
    @Test fun `envelope carries a stable event identity and schema`() {
        val event = PlatformEvent("cpe.provisioning", sn="SN1", occurredAt=Instant.now())
        val fields = PlatformEventCodec.toFields(event)
        assertEquals("1", fields["schemaVersion"])
        assertFalse(fields["eventId"].isNullOrBlank())
        assertEquals(fields, PlatformEventCodec.toFields(PlatformEventCodec.fromFields(fields)))
    }
    @Test fun `unknown schema is rejected instead of silently applied`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformEventCodec.fromFields(mapOf("type" to "cpe.provisioning", "schemaVersion" to "99", "occurredAt" to Instant.now().toString()))
        }
    }
}
