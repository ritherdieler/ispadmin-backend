package com.dscorp.wispadmin.routeros.adapter

import com.dscorp.wispadmin.routeros.port.MikrotikSession

class MockMikrotikSession : MikrotikSession {
    override fun print(path: String, query: Map<String, String>): List<Map<String, String>> = emptyList()

    override fun call(path: String, args: Map<String, String>): List<Map<String, String>> = emptyList()

    override fun add(path: String, args: Map<String, String>) = Unit

    override fun set(path: String, id: String, args: Map<String, String>) = Unit

    override fun remove(path: String, id: String) = Unit

    override fun execute(command: String): List<Map<String, String>> = emptyList()
}
