package com.dscorp.wispadmin.wispadmin.util

import com.dscorp.wispadmin.wispadmin.config.OLT_SERVICE_API_KEY
import com.dscorp.wispadmin.wispadmin.config.OLT_SERVICE_BASE_URL
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestTemplate

object HttpClient {


    //generic get with restTemplate
    fun <T> get(url: String, responseType: Class<T>): T {
        val restTemplate = RestTemplate()
        restTemplate.interceptors.add(RequestResponseLoggingInterceptor())
        val headers = HttpHeaders()
        headers.set("X-Token", OLT_SERVICE_API_KEY)
        val requestEntity = HttpEntity<Any>(headers)
        return restTemplate.exchange("${OLT_SERVICE_BASE_URL}$url", org.springframework.http.HttpMethod.GET, requestEntity, responseType).body!!
    }

    //generic post with restTemplate
    fun <T> post(url: String, body: Any? = null, responseType: Class<T>): T {
        val restTemplate = RestTemplate()
        restTemplate.interceptors.add(RequestResponseLoggingInterceptor())
        val headers = HttpHeaders()
        headers.set("X-Token", OLT_SERVICE_API_KEY)
        val requestEntity = HttpEntity<Any>(body, headers)
        return restTemplate.exchange("${OLT_SERVICE_BASE_URL}$url", org.springframework.http.HttpMethod.POST, requestEntity, responseType).body!!
    }

}