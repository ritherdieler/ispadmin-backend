package com.dscorp.wispadmin.wispadmin.trafficclient
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.simp.SimpMessagingTemplate
class CoreTrafficStreamRelayTest {
    private fun headers(session: String,role: String)=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE).apply {
        sessionId=session;sessionAttributes=mutableMapOf<String,Any>("authUserId" to 1,"authUserType" to role)
    }
    @Test fun `core shares internal monitor and forwards frames until the last subscriber disconnects`() {
        val upstream=mockk<TrafficStreamTransport>(relaxed=true)
        val messaging=mockk<SimpMessagingTemplate>(relaxed=true)
        val subscriptions=mockk<SubscriptionRepository>()
        val directory=mockk<TrafficDirectoryService>()
        every { subscriptions.existsById(7) } returns true
        every { directory.list() } returns listOf(
            TrafficDirectoryEntryDto(subscriptionId = 7, ip = "192.168.250.16", routerHint = 8),
        )
        val callback=slot<(Any)->Unit>()
        every { upstream.start(match<Map<String, Any>> { it["subscriptionId"] == 7 },capture(callback)) } just Runs
        val relay=CoreTrafficStreamRelay(upstream,messaging,subscriptions,directory)
        relay.start(mapOf("subscriptionId" to 7),headers("a","TECHNICIAN"))
        relay.start(mapOf("subscriptionId" to 7),headers("b","ADMIN"))
        callback.captured(mapOf("subscriptionId" to 7,"rxMbps" to 20.0))
        verify(exactly=1) { upstream.start(match<Map<String, Any>> { it["subscriptionId"] == 7 && it["ip"] == "192.168.250.16" && it["routerHint"] == 8 },any()) }
        verify { messaging.convertAndSend("/topic/subscription-traffic/7",any<Any>()) }
        relay.disconnect("a")
        verify(exactly=0) { upstream.stop(7) }
        relay.disconnect("b")
        verify(exactly=1) { upstream.stop(7) }
    }

    @Test fun `frontend static ip wins over leftover directory pppoe on the socket start`() {
        val upstream=mockk<TrafficStreamTransport>(relaxed=true)
        val subscriptions=mockk<SubscriptionRepository>()
        val directory=mockk<TrafficDirectoryService>()
        every { subscriptions.existsById(5) } returns true
        every { directory.list() } returns listOf(
            TrafficDirectoryEntryDto(subscriptionId = 5, ip = "", routerHint = 8, pppoeUsername = "gf5"),
        )
        val relay=CoreTrafficStreamRelay(upstream,mockk(relaxed=true),subscriptions,directory)
        relay.start(mapOf("subscriptionId" to 5, "ip" to "192.168.250.20"),headers("a","TECHNICIAN"))
        verify {
            upstream.start(
                match<Map<String, Any>> {
                    it["subscriptionId"] == 5 &&
                        it["ip"] == "192.168.250.20" &&
                        !it.containsKey("pppoeUsername")
                },
                any(),
            )
        }
    }

    @Test fun `core forwards STATIC_IP identity so traffic can poll the simple queue`() {
        val upstream=mockk<TrafficStreamTransport>(relaxed=true)
        val subscriptions=mockk<SubscriptionRepository>()
        val directory=mockk<TrafficDirectoryService>()
        every { subscriptions.existsById(5) } returns true
        every { directory.list() } returns listOf(
            TrafficDirectoryEntryDto(subscriptionId = 5, ip = "192.168.250.20", routerHint = 8, pppoeUsername = null),
        )
        val relay=CoreTrafficStreamRelay(upstream,mockk(relaxed=true),subscriptions,directory)
        relay.start(mapOf("subscriptionId" to 5),headers("a","TECHNICIAN"))
        verify {
            upstream.start(
                match<Map<String, Any>> {
                    it["subscriptionId"] == 5 &&
                        it["ip"] == "192.168.250.20" &&
                        it["routerHint"] == 8 &&
                        !it.containsKey("pppoeUsername")
                },
                any(),
            )
        }
    }
    @Test fun `customer cannot start a technical monitor`() {
        val upstream=mockk<TrafficStreamTransport>(relaxed=true)
        val relay=CoreTrafficStreamRelay(upstream,mockk(relaxed=true),mockk(relaxed=true),mockk(relaxed=true))
        assertThrows(Exception::class.java) { relay.start(mapOf("subscriptionId" to 7),headers("a","CLIENT")) }
        verify(exactly=0) { upstream.start(any<Map<String, Any>>(),any()) }
    }

    @Test fun `relay does not eagerly depend on the STOMP broker template`() {
        val src = java.nio.file.Files.readString(
            java.nio.file.Path.of(System.getProperty("user.dir"))
                .resolve("core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/trafficclient/CoreTrafficStreamRelay.kt"),
        )
        assertTrue(src.contains("@Lazy"), src)
        assertTrue(Regex("""@Lazy\s+private val messaging: SimpMessagingTemplate""").containsMatchIn(src), src)
    }
}
