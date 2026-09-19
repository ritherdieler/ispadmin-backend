package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.fasterxml.jackson.databind.JsonNode
import java.net.URI

object InternetWanIp {
    fun internetHosts(node: JsonNode, crHost: String?): List<String> {
        val collected = mutableListOf<String>()
        collectExternal(node, collected)
        return collected.distinct().filter { isInternetHost(it, crHost) }
    }

    fun crHost(connectionRequestUrl: String?): String? {
        if (connectionRequestUrl.isNullOrBlank()) return null
        return try {
            URI(connectionRequestUrl).host
        } catch (_: Exception) {
            null
        }
    }

    fun isMgmt(ip: String): Boolean {
        val addr = parse(ip) ?: return false
        return inPrefix(addr, intArrayOf(10, 20, 0, 0), 22) ||
            inPrefix(addr, intArrayOf(192, 168, 252, 0), 22)
    }

    fun isInternetHost(ip: String, crHost: String?): Boolean {
        if (ip == crHost) return false
        if (isMgmt(ip)) return false
        val addr = parse(ip) ?: return false
        if (addr[0] == 0 || addr[0] >= 224) return false
        if (addr.all { it == 0 }) return false
        if (addr[0] == 255) return false
        return true
    }

    private fun collectExternal(node: JsonNode, out: MutableList<String>, field: String? = null) {
        if (node.isObject) {
            val fields = node.fields()
            while (fields.hasNext()) {
                val entry = fields.next()
                val name = entry.key
                val child = entry.value
                if (name == "ExternalIPAddress") {
                    val value = child.path("_value").asText(null) ?: child.asText(null)
                    if (!value.isNullOrBlank() && value != "null") out.add(value.trim())
                } else if (name == "_value" && field == "ExternalIPAddress") {
                    val value = child.asText(null)
                    if (!value.isNullOrBlank() && value != "null") out.add(value.trim())
                } else {
                    collectExternal(child, out, name)
                }
            }
        } else if (node.isArray) {
            node.forEach { collectExternal(it, out, field) }
        }
    }

    private fun parse(ip: String): IntArray? {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return null
        val out = IntArray(4)
        for (i in 0..3) {
            val n = parts[i].toIntOrNull() ?: return null
            if (n !in 0..255) return null
            out[i] = n
        }
        return out
    }

    private fun inPrefix(addr: IntArray, network: IntArray, prefix: Int): Boolean {
        val packedAddr = pack(addr)
        val packedNet = pack(network)
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        return (packedAddr and mask) == (packedNet and mask)
    }

    private fun pack(octets: IntArray): Int {
        return (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]
    }
}
