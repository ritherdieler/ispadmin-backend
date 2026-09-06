package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.springframework.stereotype.Component

@Component
class Tr069ModelProfileRegistry {
    @Volatile
    private var profilesByKey: Map<String, Tr069ModelProfile> = emptyMap()

    fun reload() {
        profilesByKey = emptyMap()
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
}
