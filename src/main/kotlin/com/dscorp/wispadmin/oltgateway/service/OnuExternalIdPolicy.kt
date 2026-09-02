package com.dscorp.wispadmin.oltgateway.service

object OnuExternalIdPolicy {

    fun canonical(oltId: String, board: Int, port: Int, onuIndex: Int): String =
        "${oltId}_${board}_${port}_$onuIndex"

    fun isCanonical(externalId: String, oltId: String): Boolean {
        if (oltId.isBlank()) return false
        val prefix = "${oltId}_"
        if (!externalId.startsWith(prefix)) return false
        val position = externalId.removePrefix(prefix).split("_")
        return position.size == 3 && position.all { it.toIntOrNull() != null }
    }
}
