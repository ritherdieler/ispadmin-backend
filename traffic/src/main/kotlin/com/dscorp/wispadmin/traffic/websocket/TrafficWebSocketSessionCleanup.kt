package com.dscorp.wispadmin.traffic.websocket

fun interface TrafficWebSocketSessionCleanup {
    fun handleUserDisconnect(sessionId: String)
}
