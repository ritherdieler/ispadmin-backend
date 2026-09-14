package com.dscorp.wispadmin.routeros.adapter

object RouterOsRestPathMapper {

    fun printPath(path: String): String {
        val normalized = normalize(path)
        val withPrint = if (normalized.endsWith("/print")) normalized else "$normalized/print"
        return "/rest/$withPrint"
    }

    fun resourcePath(path: String, id: String? = null): String {
        val normalized = normalize(path).removeSuffix("/print")
        return if (id.isNullOrBlank()) {
            "/rest/$normalized"
        } else {
            "/rest/$normalized/$id"
        }
    }

    private fun normalize(path: String): String {
        return path.trim().trimStart('/').trimEnd('/')
    }
}
