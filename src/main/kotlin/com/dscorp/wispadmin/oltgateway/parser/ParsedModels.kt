package com.dscorp.wispadmin.oltgateway.parser

data class ParsedVersion(
    val product: String,
    val version: String,
    val patch: String?,
    val uptime: String?
)

data class ParsedBoard(
    val slot: Int,
    val boardName: String,
    val status: String
)

data class ParsedAutofindOnt(
    val sn: String,
    val frame: Int,
    val slot: Int,
    val port: Int,
    val vendorId: String? = null,
    val equipmentId: String? = null,
    val softwareVersion: String? = null,
    val autofindTime: String? = null
)

data class ParsedOnuBySn(
    val sn: String,
    val frame: Int,
    val slot: Int,
    val port: Int,
    val ontId: Int,
    val description: String? = null,
    val runState: String? = null,
    val controlFlag: String? = null,
    val lineProfileId: Int? = null,
    val lineProfileName: String? = null,
    val serviceProfileId: Int? = null,
    val serviceProfileName: String? = null
)

data class ParsedOnuSummary(
    val frame: Int,
    val slot: Int,
    val port: Int,
    val ontId: Int,
    val sn: String,
    val controlFlag: String? = null,
    val runState: String? = null,
    val configState: String? = null,
    val matchState: String? = null,
    val description: String? = null,
    val distanceM: Int? = null,
    val lastDownCause: String? = null,
    val lineProfileName: String? = null,
    val serviceProfileName: String? = null
)

data class ParsedOpticalInfo(
    val ontId: Int,
    val rxPowerDbm: Double? = null,
    val txPowerDbm: Double? = null,
    val oltRxPowerDbm: Double? = null,
    val temperatureC: Double? = null,
    val voltageV: Double? = null,
    val biasCurrentMa: Double? = null,
    val distanceM: Int? = null
)
