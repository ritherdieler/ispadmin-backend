package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import java.io.Serializable
import java.time.LocalDateTime

/**
 * Snapshot GenieACS consultable por backoffice/plataforma (sin árbol completo ni secretos).
 * No incluye passwords WiFi ni credenciales Connection Request.
 */
data class SubscriptionAcsDto(
    val subscriptionId: Int,
    val genieacsDeviceId: String? = null,
    val serialSuffix: String? = null,
    val smartoltSerial: String? = null,
    val provisionStatus: Tr069ProvisionStatus? = null,
    val tr069RequiresManualConfig: Boolean = false,
    val lastError: String? = null,
    val provisionedAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val lastInformAt: LocalDateTime? = null,
    val productClass: String? = null,
    val oui: String? = null,
    val manufacturer: String? = null,
    val connectionRequestUrl: String? = null,
    val wanIpCache: String? = null,
    val ssid24: String? = null,
    val ssid5: String? = null,
    val softwareVersion: String? = null,
    val hardwareVersion: String? = null,
    val lastBootAt: LocalDateTime? = null,
    val lastTaskId: String? = null,
    val lastTaskStatus: String? = null,
    val lastTaskAt: LocalDateTime? = null,
) : Serializable
