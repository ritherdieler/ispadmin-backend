package com.dscorp.wispadmin.routeros.adapter

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import me.legrange.mikrotik.ApiConnection

class LegrangeClassicSession(
    private val connection: ApiConnection
) : MikrotikSession {

    override fun print(path: String, query: Map<String, String>): List<Map<String, String>> {
        val command = buildPrintCommand(path, query)
        return connection.execute(command)
    }

    override fun add(path: String, args: Map<String, String>) {
        connection.execute(buildMutationCommand(path, "add", args))
    }

    override fun set(path: String, id: String, args: Map<String, String>) {
        val withId = LinkedHashMap<String, String>()
        withId[".id"] = id
        withId.putAll(args)
        connection.execute(buildMutationCommand(path, "set", withId))
    }

    override fun remove(path: String, id: String) {
        connection.execute(buildMutationCommand(path, "remove", mapOf(".id" to id)))
    }

    override fun execute(command: String): List<Map<String, String>> {
        return connection.execute(command)
    }

    private fun buildPrintCommand(path: String, query: Map<String, String>): String {
        val base = ensurePrintPath(path)
        if (query.isEmpty()) {
            return base
        }
        val where = query.entries.joinToString(" ") { (key, value) -> "$key=\"$value\"" }
        return "$base where $where"
    }

    private fun buildMutationCommand(path: String, action: String, args: Map<String, String>): String {
        val base = normalizePath(path).removeSuffix("/print")
        val payload = args.entries.joinToString(" ") { (key, value) -> "$key=$value" }
        return if (payload.isBlank()) {
            "/$base/$action"
        } else {
            "/$base/$action $payload"
        }
    }

    private fun ensurePrintPath(path: String): String {
        val normalized = normalizePath(path)
        return if (normalized.endsWith("/print")) {
            "/$normalized"
        } else {
            "/$normalized/print"
        }
    }

    private fun normalizePath(path: String): String {
        return path.trim().trimStart('/').trimEnd('/')
    }
}
