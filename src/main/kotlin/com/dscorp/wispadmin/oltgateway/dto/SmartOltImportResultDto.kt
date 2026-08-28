package com.dscorp.wispadmin.oltgateway.dto

data class SmartOltImportResultDto(
    val zonesImported: Int = 0,
    val onuTypesImported: Int = 0,
    val inserted: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val pagesFetched: Int = 0,
    val totalItems: Int = 0,
    val durationMs: Long = 0,
    val error: String? = null
)
