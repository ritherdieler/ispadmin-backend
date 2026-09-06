package com.dscorp.wispadmin.acs.genieacs

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class Tr069ModelProfileRegistry(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(Tr069ModelProfileRegistry::class.java)

    @Volatile
    private var profilesByKey: Map<String, Tr069ModelProfile> = emptyMap()

    @PostConstruct
    fun init() {
        reload()
        Tr069ModelProfiles.registerDynamicResolver { onuTypeName, productClass ->
            resolve(onuTypeName, productClass)
        }
    }

    fun reload() {
        profilesByKey = loadFromLocalSchema()
        logger.info("ACS TR-069 profiles loaded count={}", profilesByKey.size)
    }

    fun hasImportedProfiles(): Boolean = profilesByKey.isNotEmpty()

    fun resolve(onuTypeName: String?, productClass: String?): Tr069ModelProfile? {
        val keys = listOfNotNull(onuTypeName, productClass)
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        return keys.firstNotNullOfOrNull { key ->
            profilesByKey.entries.firstOrNull { (alias, _) ->
                key == alias || key.contains(alias) || alias.contains(key)
            }?.value
        }
    }

    internal fun replaceProfilesForTests(map: Map<String, Tr069ModelProfile>) {
        profilesByKey = map
        Tr069ModelProfiles.registerDynamicResolver { onuTypeName, productClass ->
            resolve(onuTypeName, productClass)
        }
    }

    private fun loadFromLocalSchema(): Map<String, Tr069ModelProfile> {
        return try {
            val sql = """
                SELECT product_class, wan_connection_device_index, wan_ip_connection_path,
                       client_wan_ip_connection_path, wan_gpon_link_config_path,
                       vlan_parameters_json, client_vlan_parameters_json,
                       wlan24_path, wlan5_path, wifi_security_prep_json, aliases_json
                FROM tr069_model_profile
                """.trimIndent()
            val map = linkedMapOf<String, Tr069ModelProfile>()
            jdbc.query(
                sql,
                org.springframework.jdbc.core.ResultSetExtractor { rs ->
                    while (rs.next()) {
                        val productClass = rs.getString("product_class")
                        val profile = Tr069ModelProfile(
                            productClass = productClass,
                            wanConnectionDeviceIndex = rs.getInt("wan_connection_device_index"),
                            wanIpConnectionPath = rs.getString("wan_ip_connection_path").orEmpty(),
                            wanGponLinkConfigPath = rs.getString("wan_gpon_link_config_path"),
                            vlanParameters = decodeVlan(rs.getString("vlan_parameters_json")),
                            wlan24Path = rs.getString("wlan24_path").orEmpty(),
                            wlan5Path = rs.getString("wlan5_path").orEmpty(),
                            wifiSecurityPrep = decodeWifi(rs.getString("wifi_security_prep_json")),
                            clientWanIpConnectionPath = rs.getString("client_wan_ip_connection_path"),
                            clientVlanParameters = decodeVlan(rs.getString("client_vlan_parameters_json")),
                        )
                        map[productClass.uppercase()] = profile
                        decodeAliases(rs.getString("aliases_json")).forEach { alias ->
                            map[alias.uppercase()] = profile
                        }
                    }
                    map
                },
            )
            map
        } catch (ex: Exception) {
            logger.warn("Failed loading TR-069 profiles from ACS schema: {}", ex.message)
            emptyMap()
        }
    }

    private fun decodeAliases(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return objectMapper.readValue(json, object : TypeReference<List<String>>() {})
    }

    private fun decodeVlan(json: String?): List<Tr069VlanParameterSpec> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        val stored: List<Map<String, String>> =
            objectMapper.readValue(json, object : TypeReference<List<Map<String, String>>>() {})
        return stored.map { row ->
            Tr069VlanParameterSpec(
                path = row.getValue("path"),
                valueKind = Tr069VlanValueKind.valueOf(row.getValue("valueKind")),
            )
        }
    }

    private fun decodeWifi(json: String?): List<Tr069WifiSecurityPrepSpec> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
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
}
