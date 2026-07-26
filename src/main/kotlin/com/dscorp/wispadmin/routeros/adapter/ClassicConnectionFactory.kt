package com.dscorp.wispadmin.routeros.adapter

import me.legrange.mikrotik.ApiConnection
import javax.net.SocketFactory

fun interface ClassicConnectionFactory {
    fun connect(host: String, port: Int, timeoutMs: Int): ApiConnection
}

object DefaultClassicConnectionFactory : ClassicConnectionFactory {
    override fun connect(host: String, port: Int, timeoutMs: Int): ApiConnection {
        return ApiConnection.connect(SocketFactory.getDefault(), host, port, timeoutMs)
    }
}
