package com.dscorp.wispadmin.acs.entity

import com.dscorp.wispadmin.acs.genieacs.Tr069ModelProfile
import com.dscorp.wispadmin.acs.genieacs.Tr069ProfileDraft
import com.dscorp.wispadmin.acs.genieacs.Tr069VlanParameterSpec
import com.dscorp.wispadmin.acs.genieacs.Tr069VlanValueKind
import com.dscorp.wispadmin.acs.genieacs.Tr069WifiSecurityPrepSpec
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "tr069_model_profile")
class Tr069ModelProfileRecord(
    @Id
    @Column(name = "product_class", length = 64)
    var productClass: String = "",

    @Column(name = "manufacturer", length = 128)
    var manufacturer: String? = null,

    @Column(name = "wan_connection_device_index", nullable = false)
    var wanConnectionDeviceIndex: Int = 1,

    @Column(name = "wan_ip_connection_path", nullable = false, length = 512)
    var wanIpConnectionPath: String = "",

    @Column(name = "client_wan_ip_connection_path", length = 512)
    var clientWanIpConnectionPath: String? = null,

    @Column(name = "wan_gpon_link_config_path", length = 512)
    var wanGponLinkConfigPath: String? = null,

    @Column(name = "vlan_parameters_json", nullable = false, columnDefinition = "TEXT")
    var vlanParametersJson: String = "[]",

    @Column(name = "client_vlan_parameters_json", columnDefinition = "TEXT")
    var clientVlanParametersJson: String? = null,

    @Column(name = "wlan24_path", length = 512)
    var wlan24Path: String? = null,

    @Column(name = "wlan5_path", length = 512)
    var wlan5Path: String? = null,

    @Column(name = "wifi_security_prep_json", nullable = false, columnDefinition = "TEXT")
    var wifiSecurityPrepJson: String = "[]",

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
        vlanParameters = decodeVlan(objectMapper, vlanParametersJson),
        wlan24Path = wlan24Path.orEmpty(),
        wlan5Path = wlan5Path.orEmpty(),
        wifiSecurityPrep = decodeWifi(objectMapper, wifiSecurityPrepJson),
        clientWanIpConnectionPath = clientWanIpConnectionPath,
        clientVlanParameters = decodeVlan(objectMapper, clientVlanParametersJson ?: "[]"),
    )

    fun toDto(objectMapper: ObjectMapper): AcsModelProfileDto = AcsModelProfileDto(
        productClass = productClass,
        manufacturer = manufacturer,
        wanConnectionDeviceIndex = wanConnectionDeviceIndex,
        wanIpConnectionPath = wanIpConnectionPath,
        wanGponLinkConfigPath = wanGponLinkConfigPath,
        vlanParameters = decodeVlan(objectMapper, vlanParametersJson),
        wlan24Path = wlan24Path,
        wlan5Path = wlan5Path,
        wifiSecurityPrep = decodeWifi(objectMapper, wifiSecurityPrepJson),
        clientWanIpConnectionPath = clientWanIpConnectionPath,
        clientVlanParameters = decodeVlan(objectMapper, clientVlanParametersJson ?: "[]"),
        aliases = decodeStrings(objectMapper, aliasesJson),
        sourceDeviceId = sourceDeviceId,
        sourceSerial = sourceSerial,
        warnings = decodeStrings(objectMapper, warningsJson),
        importedAt = importedAt.toString(),
        importedBy = importedBy,
    )

    companion object {
        private val stringListType = object : TypeReference<List<String>>() {}

        fun fromDraft(
            draft: Tr069ProfileDraft,
            importedBy: String?,
            objectMapper: ObjectMapper,
            aliases: List<String> = emptyList(),
        ): Tr069ModelProfileRecord = Tr069ModelProfileRecord(
            productClass = draft.productClass,
            manufacturer = draft.manufacturer,
            wanConnectionDeviceIndex = draft.wanConnectionDeviceIndex,
            wanIpConnectionPath = draft.wanIpConnectionPath,
            clientWanIpConnectionPath = draft.clientWanIpConnectionPath,
            wanGponLinkConfigPath = draft.wanGponLinkConfigPath,
            vlanParametersJson = encodeVlan(objectMapper, draft.vlanParameters),
            clientVlanParametersJson = encodeVlan(objectMapper, draft.clientVlanParameters),
            wlan24Path = draft.wlan24Path,
            wlan5Path = draft.wlan5Path,
            wifiSecurityPrepJson = encodeWifi(objectMapper, draft.wifiSecurityPrep),
            aliasesJson = if (aliases.isEmpty()) null else objectMapper.writeValueAsString(aliases.distinct()),
            sourceDeviceId = draft.deviceId,
            sourceSerial = draft.serialNumber,
            warningsJson = if (draft.warnings.isEmpty()) null else objectMapper.writeValueAsString(draft.warnings),
            importedAt = LocalDateTime.now(),
            importedBy = importedBy,
        )

        fun encodeWifi(objectMapper: ObjectMapper, parameters: List<Tr069WifiSecurityPrepSpec>): String =
            objectMapper.writeValueAsString(
                parameters.map { mapOf("parameterSuffix" to it.parameterSuffix, "value" to it.value, "type" to it.type) },
            )

        fun decodeWifi(objectMapper: ObjectMapper, json: String): List<Tr069WifiSecurityPrepSpec> {
            if (json.isBlank() || json == "[]") return emptyList()
            val stored: List<Map<String, String>> =
                objectMapper.readValue(json, object : TypeReference<List<Map<String, String>>>() {})
            return stored.map { row ->
                Tr069WifiSecurityPrepSpec(
                    parameterSuffix = row.getValue("parameterSuffix"),
                    value = row.getValue("value"),
                    type = row.getValue("type"),
                )
            }
        }

        fun encodeVlan(objectMapper: ObjectMapper, parameters: List<Tr069VlanParameterSpec>): String =
            objectMapper.writeValueAsString(
                parameters.map { mapOf("path" to it.path, "valueKind" to it.valueKind.name) },
            )

        fun decodeVlan(objectMapper: ObjectMapper, json: String): List<Tr069VlanParameterSpec> {
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

        fun decodeStrings(objectMapper: ObjectMapper, json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return objectMapper.readValue(json, stringListType)
        }
    }
}

data class AcsModelProfileDto(
    val productClass: String,
    val manufacturer: String? = null,
    val wanConnectionDeviceIndex: Int,
    val wanIpConnectionPath: String,
    val wanGponLinkConfigPath: String? = null,
    val vlanParameters: List<Tr069VlanParameterSpec> = emptyList(),
    val wlan24Path: String? = null,
    val wlan5Path: String? = null,
    val wifiSecurityPrep: List<Tr069WifiSecurityPrepSpec> = emptyList(),
    val clientWanIpConnectionPath: String? = null,
    val clientVlanParameters: List<Tr069VlanParameterSpec> = emptyList(),
    val aliases: List<String> = emptyList(),
    val sourceDeviceId: String? = null,
    val sourceSerial: String? = null,
    val warnings: List<String> = emptyList(),
    val importedAt: String? = null,
    val importedBy: String? = null,
)

data class AcsModelProfileImportResultDto(
    val saved: AcsModelProfileDto,
    val replacedExisting: Boolean,
)

data class AcsModelProfilePreviewDto(
    val draft: AcsModelProfileDto,
    val readyToImport: Boolean,
)

data class AcsProfileCsvCommand(
    val csv: String,
    val aliases: List<String> = emptyList(),
    val importedBy: String? = null,
)
