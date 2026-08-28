package com.dscorp.wispadmin.oltgateway.smartolt

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class RestSmartOltCatalogClient(
    @Value("\${olt.service.base-url}") private val baseUrl: String,
    @Value("\${olt.service.api-key}") private val apiKey: String
) : SmartOltCatalogClient {

    private val restTemplate = RestTemplate()

    override fun fetchZones(): SmartOltZonesResponseDto =
        get("system/get_zones", SmartOltZonesResponseDto::class.java)

    override fun fetchOnuTypes(): SmartOltOnuTypesResponseDto =
        get("system/get_onu_types", SmartOltOnuTypesResponseDto::class.java)

    override fun fetchAllOnusDetails(page: Int, pageSize: Int): SmartOltAllOnusPageDto =
        get(
            "onu/get_all_onus_details?page=$page&page_size=$pageSize",
            SmartOltAllOnusPageDto::class.java
        )

    private fun <T> get(path: String, responseType: Class<T>): T {
        val headers = HttpHeaders()
        headers.set("X-Token", apiKey)
        val request = HttpEntity<Void>(headers)
        val url = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        return restTemplate.exchange(url, HttpMethod.GET, request, responseType).body!!
    }
}
