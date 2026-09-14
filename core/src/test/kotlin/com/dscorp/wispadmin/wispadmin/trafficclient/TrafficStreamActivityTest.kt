package com.dscorp.wispadmin.wispadmin.trafficclient
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.stomp.StompSession
class TrafficStreamActivityTest {
    @Test fun `active monitors refresh upstream activity instead of expiring after five minutes`() {
        val transport=StompTrafficStreamTransport(TrafficClientProperties())
        try {
            val session=mockk<StompSession>(relaxed=true)
            every { session.isConnected } returns true
            transport.javaClass.getDeclaredField("session").apply { isAccessible=true }.set(transport,session)
            transport.start(7) { }
            clearMocks(session,answers=false)
            transport.reconnect()
            verify { session.send("/app/subscription-traffic/start",mapOf("subscriptionId" to 7)) }
        } finally { transport.close() }
    }
}
