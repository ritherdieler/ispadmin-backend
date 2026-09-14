package com.dscorp.wispadmin.wispadmin.websocket

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.messaging.SessionDisconnectEvent

class WebSocketEventListenerTest {

    @Test
    fun `notifica a todos los cleanups al desconectar`() {
        val first = mockk<WebSocketSessionCleanup>(relaxed = true)
        val second = mockk<WebSocketSessionCleanup>(relaxed = true)
        val provider = mockk<ObjectProvider<WebSocketSessionCleanup>>()
        every { provider.iterator() } returns mutableListOf(first, second).iterator()

        val listener = WebSocketEventListener(provider)
        val event = SessionDisconnectEvent(this, mockk(relaxed = true), "sess-1", CloseStatus.NORMAL)

        listener.handleWebSocketDisconnectListener(event)

        verify(exactly = 1) { first.handleUserDisconnect("sess-1") }
        verify(exactly = 1) { second.handleUserDisconnect("sess-1") }
    }
}
