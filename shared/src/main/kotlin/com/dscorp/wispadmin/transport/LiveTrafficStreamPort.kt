package com.dscorp.wispadmin.transport

interface LiveTrafficStreamPort {
    fun start(subscriptionId: Int, receive: (Any) -> Unit) {
        start(mapOf("subscriptionId" to subscriptionId), receive)
    }

    fun start(command: Map<String, Any>, receive: (Any) -> Unit)

    fun stop(subscriptionId: Int)
}
