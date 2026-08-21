package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069VlanParameterSpec
import java.time.LocalDateTime

data class Tr069ModelProfileDto(
    val productClass: String,
    val manufacturer: String? = null,
    val wanConnectionDeviceIndex: Int,
    val wanIpConnectionPath: String,
    val wanGponLinkConfigPath: String? = null,
    val vlanParameters: List<Tr069VlanParameterSpec> = emptyList(),
    val wlan24Path: String? = null,
    val wlan5Path: String? = null,
    val aliases: List<String> = emptyList(),
    val sourceDeviceId: String? = null,
    val sourceSerial: String? = null,
    val warnings: List<String> = emptyList(),
    val importedAt: LocalDateTime? = null,
    val importedBy: String? = null,
)

data class Tr069ModelProfileImportResultDto(
    val saved: Tr069ModelProfileDto,
    val replacedExisting: Boolean,
)

data class Tr069ModelProfilePreviewDto(
    val draft: Tr069ModelProfileDto,
    val readyToImport: Boolean,
)
