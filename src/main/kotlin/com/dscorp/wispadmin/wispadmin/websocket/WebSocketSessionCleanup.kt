package com.dscorp.wispadmin.wispadmin.websocket

fun interface WebSocketSessionCleanup {
    fun handleUserDisconnect(sessionId: String)
}
