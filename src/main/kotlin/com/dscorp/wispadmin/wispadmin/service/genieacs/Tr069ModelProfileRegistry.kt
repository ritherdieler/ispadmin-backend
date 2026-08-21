package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.Tr069ModelProfileEntity
import com.dscorp.wispadmin.wispadmin.repository.Tr069ModelProfileRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class Tr069ModelProfileRegistry(
    private val repository: Tr069ModelProfileRepository,
    private val objectMapper: ObjectMapper,
) {
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
        profilesByKey = buildMapFromEntities(repository.findAll())
    }

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

    private fun buildMapFromEntities(entities: List<Tr069ModelProfileEntity>): Map<String, Tr069ModelProfile> {
        val map = linkedMapOf<String, Tr069ModelProfile>()
        entities.forEach { entity ->
            val profile = entity.toModelProfile(objectMapper)
            map[entity.productClass.uppercase()] = profile
            decodeAliasKeys(entity.aliasesJson).forEach { alias ->
                map[alias.uppercase()] = profile
            }
        }
        return map
    }

    private fun decodeAliasKeys(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return objectMapper.readValue(json, object : TypeReference<List<String>>() {})
    }
}
