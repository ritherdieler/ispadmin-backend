package com.dscorp.wispadmin.transport

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClientException

class InvalidSubsystemResponse : RestClientException("Invalid subsystem JSON response")
object InternalJson {
    private val mapper=ObjectMapper()
    fun validate(response: ResponseEntity<String>): ResponseEntity<String> {
        if(response.statusCodeValue==204) return response
        val body=response.body ?: throw InvalidSubsystemResponse()
        val node=try { mapper.readTree(body) } catch(_: Exception) { throw InvalidSubsystemResponse() }
        if(node==null || (!node.isObject && !node.isArray)) throw InvalidSubsystemResponse()
        return response
    }
}
