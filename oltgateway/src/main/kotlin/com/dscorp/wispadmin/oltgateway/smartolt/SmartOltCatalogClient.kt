package com.dscorp.wispadmin.oltgateway.smartolt

interface SmartOltCatalogClient {
    fun fetchZones(): SmartOltZonesResponseDto
    fun fetchOnuTypes(): SmartOltOnuTypesResponseDto
    fun fetchAllOnusDetails(page: Int, pageSize: Int): SmartOltAllOnusPageDto
}
