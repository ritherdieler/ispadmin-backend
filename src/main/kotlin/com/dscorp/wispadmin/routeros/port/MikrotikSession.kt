package com.dscorp.wispadmin.routeros.port

interface MikrotikSession {
    fun print(path: String, query: Map<String, String> = emptyMap()): List<Map<String, String>>
    fun add(path: String, args: Map<String, String>)
    fun set(path: String, id: String, args: Map<String, String>)
    fun remove(path: String, id: String)
    fun execute(command: String): List<Map<String, String>>
}
