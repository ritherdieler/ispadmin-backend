package com.dscorp.wispadmin.wispadmin.service.genieacs

data class GenieAcsDevice(
    val id: String,
    val serialNumber: String? = null,
    val productClass: String? = null,
    val lastInform: String? = null,
    val manufacturer: String? = null,
    val oui: String? = null,
    val softwareVersion: String? = null,
    val hardwareVersion: String? = null,
    val lastBoot: String? = null,
    val connectionRequestUrl: String? = null,
)

sealed class Tr069SerialMatch {
    data class Found(val device: GenieAcsDevice) : Tr069SerialMatch()
    data class None(val suffix: String) : Tr069SerialMatch()
    data class Ambiguous(val suffix: String, val count: Int) : Tr069SerialMatch()
    object InvalidSerial : Tr069SerialMatch()
}

object Tr069SerialMatcher {

    fun normalizeSuffix(serial: String?): String? = com.dscorp.wispadmin.transport.SerialSuffix.normalizeSuffix(serial)

    fun findUnique(smartOltSn: String?, devices: List<GenieAcsDevice>): Tr069SerialMatch {
        val suffix = normalizeSuffix(smartOltSn) ?: return Tr069SerialMatch.InvalidSerial
        val matches = devices.filter { device ->
            normalizeSuffix(device.serialNumber) == suffix ||
                normalizeSuffix(device.id.substringAfterLast('-', device.id)) == suffix ||
                device.id.uppercase().endsWith(suffix)
        }
        return when (matches.size) {
            0 -> Tr069SerialMatch.None(suffix)
            1 -> Tr069SerialMatch.Found(matches.first())
            else -> Tr069SerialMatch.Ambiguous(suffix, matches.size)
        }
    }
}
