package com.dscorp.wispadmin.wispadmin.trafficclient

data class TrafficDirectoryEntryDto(
    val subscriptionId: Int,
    val ip: String,
    val routerHint: Int? = null,
    val planId: Int? = null,
    val planName: String? = null,
    val planDownloadMbps: Int? = null,
    val planUploadMbps: Int? = null,
    val displayName: String? = null,
)
