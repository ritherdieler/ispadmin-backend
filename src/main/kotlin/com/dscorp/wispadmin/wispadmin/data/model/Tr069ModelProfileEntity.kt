package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.Tr069ModelProfileDto
import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069ModelProfile
import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069VlanParameterSpec
import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069VlanValueKind
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "tr069_model_profile")
data class Tr069ModelProfileEntity(
    @Id
    @Column(name = "product_class", length = 64)
    var productClass: String = "",

    @Column(name = "manufacturer", length = 128)
    var manufacturer: String? = null,

    @Column(name = "wan_connection_device_index", nullable = false)
    var wanConnectionDeviceIndex: Int = 1,

    @Column(name = "wan_ip_connection_path", nullable = false, length = 512)
    var wanIpConnectionPath: String = "",

    @Column(name = "wan_gpon_link_config_path", length = 512)
    var wanGponLinkConfigPath: String? = null,

    @Column(name = "vlan_parameters_json", nullable = false, columnDefinition = "TEXT")
    var vlanParametersJson: String = "[]",

    @Column(name = "wlan24_path", length = 512)
    var wlan24Path: String? = null,

    @Column(name = "wlan5_path", length = 512)
    var wlan5Path: String? = null,

    @Column(name = "aliases_json", columnDefinition = "TEXT")
    var aliasesJson: String? = null,

    @Column(name = "source_device_id", length = 128)
    var sourceDeviceId: String? = null,

    @Column(name = "source_serial", length = 64)
    var sourceSerial: String? = null,

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    var warningsJson: String? = null,

    @Column(name = "imported_at", nullable = false)
    var importedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "imported_by", length = 64)
    var importedBy: String? = null,
) {
    fun toModelProfile(objectMapper: ObjectMapper): Tr069ModelProfile = Tr069ModelProfile(
        productClass = productClass,
        wanConnectionDeviceIndex = wanConnectionDeviceIndex,
        wanIpConnectionPath = wanIpConnectionPath,
        wanGponLinkConfigPath = wanGponLinkConfigPath,
        vlanParameters = Tr069ModelProfileEntity.decodeVlanParameters(objectMapper, vlanParametersJson),
        wlan24Path = wlan24Path.orEmpty(),
        wlan5Path = wlan5Path.orEmpty(),
    )

    fun toDto(objectMapper: ObjectMapper): Tr069ModelProfileDto = Tr069ModelProfileDto(
        productClass = productClass,
        manufacturer = manufacturer,
        wanConnectionDeviceIndex = wanConnectionDeviceIndex,
        wanIpConnectionPath = wanIpConnectionPath,
        wanGponLinkConfigPath = wanGponLinkConfigPath,
        vlanParameters = Tr069ModelProfileEntity.decodeVlanParameters(objectMapper, vlanParametersJson),
        wlan24Path = wlan24Path,
        wlan5Path = wlan5Path,
        aliases = decodeAliases(aliasesJson, objectMapper),
        sourceDeviceId = sourceDeviceId,
        sourceSerial = sourceSerial,
        warnings = decodeWarnings(warningsJson, objectMapper),
        importedAt = importedAt,
        importedBy = importedBy,
    )

    companion object {
        private val stringListType = object : TypeReference<List<String>>() {}

        fun fromDraft(
            draft: com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069ProfileDraft,
            importedBy: String?,
            objectMapper: ObjectMapper,
            aliases: List<String> = emptyList(),
        ): Tr069ModelProfileEntity = Tr069ModelProfileEntity(
            productClass = draft.productClass,
            manufacturer = draft.manufacturer,
            wanConnectionDeviceIndex = draft.wanConnectionDeviceIndex,
            wanIpConnectionPath = draft.wanIpConnectionPath,
            wanGponLinkConfigPath = draft.wanGponLinkConfigPath,
            vlanParametersJson = encodeVlanParameters(objectMapper, draft.vlanParameters),
            wlan24Path = draft.wlan24Path,
            wlan5Path = draft.wlan5Path,
            aliasesJson = if (aliases.isEmpty()) null else objectMapper.writeValueAsString(aliases.distinct()),
            sourceDeviceId = draft.deviceId,
            sourceSerial = draft.serialNumber,
            warningsJson = if (draft.warnings.isEmpty()) null else objectMapper.writeValueAsString(draft.warnings),
            importedAt = LocalDateTime.now(),
            importedBy = importedBy,
        )

        fun encodeVlanParameters(objectMapper: ObjectMapper, parameters: List<Tr069VlanParameterSpec>): String =
            objectMapper.writeValueAsString(
                parameters.map { mapOf("path" to it.path, "valueKind" to it.valueKind.name) },
            )

        fun decodeVlanParameters(objectMapper: ObjectMapper, json: String): List<Tr069VlanParameterSpec> {
            if (json.isBlank() || json == "[]") return emptyList()
            val stored: List<Map<String, String>> =
                objectMapper.readValue(json, object : TypeReference<List<Map<String, String>>>() {})
            return stored.map { row ->
                Tr069VlanParameterSpec(
                    path = row.getValue("path"),
                    valueKind = Tr069VlanValueKind.valueOf(row.getValue("valueKind")),
                )
            }
        }

        private fun decodeAliases(json: String?, objectMapper: ObjectMapper): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return objectMapper.readValue(json, stringListType)
        }

        private fun decodeWarnings(json: String?, objectMapper: ObjectMapper): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return objectMapper.readValue(json, stringListType)
        }
    }
}
