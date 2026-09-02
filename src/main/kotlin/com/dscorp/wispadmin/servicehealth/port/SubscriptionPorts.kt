package com.dscorp.wispadmin.servicehealth.port

import java.time.LocalDateTime

data class AcsRegistryEntry(
    val subscriptionId: Int,
    val deviceId: String?,
    val lastInformAt: LocalDateTime?,
    val productClass: String?,
    val manufacturer: String?,
    val softwareVersion: String?,
    val lab: Boolean
)

interface AcsSubscriptionPort {
    fun find(subscriptionId: Int): AcsRegistryEntry?
    fun findDeviceId(subscriptionId: Int): String?
    fun findSubscriptionIdsByDeviceId(deviceId: String): List<Int>
    fun labSubscriptionIds(): List<Int>
    fun isLab(subscriptionId: Int?): Boolean
    fun recordInform(
        subscriptionId: Int,
        lastInformAt: LocalDateTime,
        productClass: String,
        softwareVersion: String,
        updatedAt: LocalDateTime
    )
}

data class SubscriptionHealthRef(
    val id: Int,
    val onuSn: String?,
    val ip: String?,
    val vlan: String?,
    val hostDeviceId: Int?,
    val planId: Int?,
    val planDownloadMbps: Int?,
    val planUploadMbps: Int?,
    val napBoxId: Int?,
    val serviceStatus: String
)

interface SubscriptionDirectoryPort {
    fun allIds(): List<Int>
    fun exists(subscriptionId: Int): Boolean
    fun find(subscriptionId: Int): SubscriptionHealthRef?
    fun lockIdentityOwner(subscriptionId: Int): SubscriptionHealthRef?
    fun findIdsByOnuSerial(sn: String): List<Int>
    fun findIdsByOnuSerialOrSuffix(sn: String, suffix: String): List<Int>
}
