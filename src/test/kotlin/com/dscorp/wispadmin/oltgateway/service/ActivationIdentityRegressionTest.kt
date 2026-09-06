package com.dscorp.wispadmin.oltgateway.service
import com.dscorp.wispadmin.events.RecordingEventBus
import com.dscorp.wispadmin.oltgateway.client.AcsCpeClient
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class ActivationIdentityRegressionTest {
    @Test fun `unknown external id is never sent to a serial lookup`() {
        val acs = mockk<AcsCpeClient>(relaxed=true)
        val service=OnuActivationService(mockk(),acs,RecordingEventBus()) { it.run() }
        assertNull(service.statusByExternalId("external-not-a-serial"))
        verify(exactly=0) { acs.status("external-not-a-serial") }
    }
}
