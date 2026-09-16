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

    @Test fun `upstream start forwards static ip identity on the socket command`() {
        val transport=StompTrafficStreamTransport(TrafficClientProperties())
        try {
            val session=mockk<StompSession>(relaxed=true)
            every { session.isConnected } returns true
            transport.javaClass.getDeclaredField("session").apply { isAccessible=true }.set(transport,session)
            transport.start(mapOf("subscriptionId" to 5, "ip" to "192.168.250.20", "routerHint" to 8)) { }
            verify {
                session.send(
                    "/app/subscription-traffic/start",
                    match<Map<String, Any>> {
                        it["subscriptionId"] == 5 &&
                            it["ip"] == "192.168.250.20" &&
                            it["routerHint"] == 8
                    },
                )
            }
        } finally { transport.close() }
    }
}
