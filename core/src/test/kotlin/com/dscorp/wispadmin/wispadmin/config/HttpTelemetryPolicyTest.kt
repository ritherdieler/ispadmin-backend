package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpTelemetryPolicyTest {

    @Test
    fun `error dispatch is never reported twice`() {
        assertFalse(HttpTelemetryPolicy.shouldReportHttpError("/ispadmin/error", 400))
        assertFalse(HttpTelemetryPolicy.shouldReportHttpError("/ispadmin-staging/error", 500))
    }

    @Test
    fun `internal subsystem client errors are not reported but server errors are`() {
        assertFalse(HttpTelemetryPolicy.shouldReportHttpError("/ispadmin/api/olt-gateway/onus/VSOL1/cpe/telemetry", 400))
        assertFalse(HttpTelemetryPolicy.shouldReportHttpError("/ispadmin/api/traffic/v1/by-subscription/1/latest", 404))
        assertTrue(HttpTelemetryPolicy.shouldReportHttpError("/ispadmin/api/acs/v1/cpe/inform-notify", 502))
    }

    @Test
    fun `public endpoints keep reporting client errors`() {
        assertTrue(HttpTelemetryPolicy.shouldReportHttpError("/ispadmin/subscription", 409))
    }

    @Test
    fun `only public client errors and any server error force span retention`() {
        assertFalse(HttpTelemetryPolicy.forceRetainSpan("/api/olt-gateway/onus/{sn}/cpe/telemetry", 400))
        assertTrue(HttpTelemetryPolicy.forceRetainSpan("/api/olt-gateway/onus/{sn}/cpe/telemetry", 500))
        assertTrue(HttpTelemetryPolicy.forceRetainSpan("/subscription", 409))
        assertFalse(HttpTelemetryPolicy.forceRetainSpan("/subscription", 200))
    }
}
