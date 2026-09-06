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
        every { subscriptions.existsById(7) } returns true
        val callback=slot<(Any)->Unit>()
        every { upstream.start(7,capture(callback)) } just Runs
        val relay=CoreTrafficStreamRelay(upstream,messaging,subscriptions)
        relay.start(mapOf("subscriptionId" to 7),headers("a","TECHNICIAN"))
        relay.start(mapOf("subscriptionId" to 7),headers("b","ADMIN"))
        callback.captured(mapOf("subscriptionId" to 7,"rxMbps" to 20.0))
        verify(exactly=1) { upstream.start(7,any()) }
        verify { messaging.convertAndSend("/topic/subscription-traffic/7",any<Any>()) }
        relay.disconnect("a")
        verify(exactly=0) { upstream.stop(7) }
        relay.disconnect("b")
        verify(exactly=1) { upstream.stop(7) }
    }
    @Test fun `customer cannot start a technical monitor`() {
        val upstream=mockk<TrafficStreamTransport>(relaxed=true)
        val relay=CoreTrafficStreamRelay(upstream,mockk(relaxed=true),mockk(relaxed=true))
        assertThrows(Exception::class.java) { relay.start(mapOf("subscriptionId" to 7),headers("a","CLIENT")) }
        verify(exactly=0) { upstream.start(any(),any()) }
    }

    @Test fun `relay does not eagerly depend on the STOMP broker template`() {
        val src = java.nio.file.Files.readString(
            java.nio.file.Path.of(System.getProperty("user.dir"))
                .resolve("src/main/kotlin/com/dscorp/wispadmin/wispadmin/trafficclient/CoreTrafficStreamRelay.kt"),
        )
        assertTrue(src.contains("@Lazy"), src)
        assertTrue(Regex("""@Lazy\s+private val messaging: SimpMessagingTemplate""").containsMatchIn(src), src)
    }
}
