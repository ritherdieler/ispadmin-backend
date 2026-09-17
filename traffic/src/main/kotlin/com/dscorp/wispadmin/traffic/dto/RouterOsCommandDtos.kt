package com.dscorp.wispadmin.traffic.dto

data class RouterOsPrintRequest(
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val proplist: List<String> = emptyList(),
)

data class RouterOsAddRequest(
    val path: String,
    val args: Map<String, String> = emptyMap(),
)

data class RouterOsSetRequest(
    val path: String,
    val id: String,
    val args: Map<String, String> = emptyMap(),
)

data class RouterOsRemoveRequest(
    val path: String,
    val id: String,
)

data class RouterOsCallRequest(
    val path: String,
    val args: Map<String, String> = emptyMap(),
)

data class RouterOsRowsResponse(
    val rows: List<Map<String, String>> = emptyList(),
)

data class RouterOsAckResponse(
    val ok: Boolean = true,
)
