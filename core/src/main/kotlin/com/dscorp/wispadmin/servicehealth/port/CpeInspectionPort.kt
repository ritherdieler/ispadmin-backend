package com.dscorp.wispadmin.servicehealth.port

import com.fasterxml.jackson.databind.JsonNode

interface CpeInspectionPort {
    fun summary(sn: String): JsonNode
    fun tree(sn: String, parent: String?, query: String?): JsonNode
    fun currentFaults(sn: String): JsonNode
    fun faultHistory(sn: String, page: Int, size: Int): JsonNode
}
