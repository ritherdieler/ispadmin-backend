package com.dscorp.wispadmin.traffic.port

data class TrafficDirectoryTarget(
    val subscriptionId: Int,
    val ip: String,
    val routerHint: Int? = null,
    val planId: Int? = null,
    val planName: String? = null,
    val planDownloadMbps: Int? = null,
    val planUploadMbps: Int? = null,
    val displayName: String? = null,
)

interface TrafficDirectoryPort {
    fun list(): List<TrafficDirectoryTarget>
}
